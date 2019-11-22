package template;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import logist.plan.Plan;
import logist.simulation.Vehicle;
import logist.task.Task;
import logist.task.TaskSet;
import logist.topology.Topology.City;

// Class representing a solution of the pickup and delivery problem for a bunch of vehicle
// coordinated in a centralized way.
public class Solution {
	private Map<Vehicle, TaskList> tasksPerVehicle; // Tasks given to each vehicle
	
	public Solution() {
		this.tasksPerVehicle = new HashMap<Vehicle, TaskList>();
	}
	
	// Deep copy
	public Solution(Solution solution) {
		this();
		
		// Get a deep copy of each TaskList
		for(Map.Entry<Vehicle, TaskList> entry : solution.tasksPerVehicle.entrySet()) {
			Vehicle v = entry.getKey();
			TaskList taskList = new TaskList(entry.getValue());
			
			this.tasksPerVehicle.put(v, taskList);
		}
	}
	
	// Set the tasks assigned to a vehicle. One task is delivered right after being picked
	// up. The tasks are with the same order as they are in the given list
	public void putVehicle(Vehicle vehicle, List<Task> tasks) {
		List<OrderedTask> taskList = new ArrayList<OrderedTask>();
		
		for (int i = 0; i < tasks.size(); i++) {
			taskList.add(new OrderedTask(tasks.get(i), 2*i, 2*i + 1));
		}
		
		this.tasksPerVehicle.put(vehicle, new TaskList(taskList));
	}
	
	public void addTask(Task task) {
		int vehicle_id = (new Random()).nextInt(tasksPerVehicle.size());
		Vehicle vehicle = null;
		int i = 0;
		for(Vehicle v : tasksPerVehicle.keySet()) {
			if(i == vehicle_id) {
				vehicle = v;
				break;
			}
			
			i++;
		}
		
		tasksPerVehicle.get(vehicle).insertTask(task, 0, 1);
	}
	
	public boolean removeTask(Task task) {
		for(TaskList taskList : tasksPerVehicle.values()) {
			for(int i = 0; i < taskList.tasks.size(); i++) {
				if(taskList.tasks.get(i).task == task) {
					taskList.removeTask(i);
					return true;
				}
			}
		}
		
		return false;
	}
	
	// Get all solutions neighbour of this one.
	public List<Solution> getNeighbours() {
		List<Solution> neighbours = new ArrayList<Solution>();
		
		Random random = new Random();
		List<Vehicle> nonEmptyVehicles = new ArrayList<Vehicle>();
		
		// List of vehicle with at least one task in charge
		for(Vehicle v : tasksPerVehicle.keySet()) {
			if(!tasksPerVehicle.get(v).tasks.isEmpty()) {
				nonEmptyVehicles.add(v);
			}
		}
		
		// Compute the total number of tasks
		int numberOfTasks = 0;
		for(TaskList taskList : tasksPerVehicle.values()) {
			numberOfTasks += taskList.tasks.size();
		}
		
		if(numberOfTasks == 0) {
			neighbours.add(this);
			return neighbours;
		}
		
		// Choose one vehicle at random, weighted with the number of tasks one vehicle has
		// in charge.
		int rnd = random.nextInt(numberOfTasks);
		int cummulative = 0;
		Vehicle vehicle = null;
		for(Map.Entry<Vehicle, TaskList> entry: tasksPerVehicle.entrySet()) {
			cummulative += entry.getValue().tasks.size();
			if(rnd < cummulative) {
				vehicle = entry.getKey();
				break;
			}
		}
		
		// Get all neighbour solutions with one task being reordered in its vehicle
		int idx = random.nextInt(tasksPerVehicle.get(vehicle).tasks.size());
		neighbours.addAll(this.getPermutatedActionNeighbours(vehicle, idx));
		
		// Choose another vehicle (possibly the same)
		rnd = random.nextInt(numberOfTasks);
		cummulative = 0;
		for(Map.Entry<Vehicle, TaskList> entry: tasksPerVehicle.entrySet()) {
			cummulative += entry.getValue().tasks.size();
			if(rnd < cummulative) {
				vehicle = entry.getKey();
				break;
			}
		}
		
		// Get all neighbour solutions with one task being removed from its vehicle
		// and put anywhere in other vehicles
		idx = random.nextInt(tasksPerVehicle.get(vehicle).tasks.size());
		neighbours.addAll(this.getPermutatedVehicleNeighbours(vehicle, idx));
		
		return neighbours;
	}
	
	// Get all possible neighbours which are the result of reordering one task in its
	// vehicle.
	private List<Solution> getPermutatedActionNeighbours(Vehicle vehicle, int idx) {
		List<Solution> neighbours = new ArrayList<Solution>();
		Solution neighbour;
		
		TaskList taskList = tasksPerVehicle.get(vehicle);
		
		// Get all the permutation and create one solution for each
		for(TaskList pTaskList : taskList.getAllPermutations(vehicle.capacity(), idx)) {
			neighbour = new Solution(this);
			neighbour.tasksPerVehicle.put(vehicle, pTaskList);
			
			neighbours.add(neighbour);
		}
		
		return neighbours;
	}
	
	private List<Solution> getPermutatedVehicleNeighbours(Vehicle v1, int idx) {
		List<Solution> neighbours = new ArrayList<Solution>();
		Solution neighbourTemplate = new Solution(this), neighbour;
		
		// Remove the idx-th task from v1 and save it
		Task task = neighbourTemplate.tasksPerVehicle.get(v1).removeTask(idx);
		
		// For each vehicle
		for(Map.Entry<Vehicle, TaskList> entry: tasksPerVehicle.entrySet()) {
			Vehicle v2 = entry.getKey();
			
			// v2 has to be different from v1
			if(v1 == v2)
				continue;
			
			// Get all possible insertions of the task in v2, and create a solution for each one
			for(TaskList pTaskList : entry.getValue().getAllInsertions(v2.capacity(), task)) {
				neighbour = new Solution(neighbourTemplate);
				neighbour.tasksPerVehicle.put(v2, pTaskList);
				
				neighbours.add(neighbour);
			}
		}
		
		return neighbours;
	}
	
	public int getNumberOfTasks() {
		int numberOfTasks = 0;
		
		for(TaskList taskList : tasksPerVehicle.values()) {
			numberOfTasks += taskList.tasks.size();
		}
		
		return numberOfTasks;
	}
	
	public void updateTask(Task oldTask, Task newTask) {
		for(TaskList taskList : tasksPerVehicle.values()) {
			for(int i = 0; i < taskList.tasks.size(); i++) {
				Task oldT = taskList.tasks.get(i).task;
				
				if(oldT == oldTask) {
					int pickUpOrder = taskList.tasks.get(i).pickUpOrder;
					int deliverOrder = taskList.tasks.get(i).deliverOrder;
					taskList.removeTask(i);
					taskList.insertTask(newTask, pickUpOrder, deliverOrder);
				}
			}
		}
	}
	
	public void updateTasks(TaskSet taskSet) {
		List<Task> tasks = new ArrayList<Task>();
		for(Task t : taskSet) {
			tasks.add(t);
		}
		boolean success;
		for(Task t : taskSet) {
			success = false;
			innerLoop:
			for(TaskList taskList : tasksPerVehicle.values()) {
				for(int i = 0; i < taskList.tasks.size(); i++) {
					Task oldT = taskList.tasks.get(i).task;
					
					if(t.pickupCity == oldT.pickupCity
							&& t.deliveryCity == oldT.deliveryCity
							&& t.weight == oldT.weight) {
						if(!tasks.contains(oldT)) {
							int pickUpOrder = taskList.tasks.get(i).pickUpOrder;
							int deliverOrder = taskList.tasks.get(i).deliverOrder;
							taskList.removeTask(i);
							taskList.insertTask(t, pickUpOrder, deliverOrder);
							success = true;
							break innerLoop;
						}
						else {
							System.out.println("Task already contained");
							System.out.println("Old: " + oldT.toString());
							System.out.println("New: " + t.toString());
						}
					}
				}
			}
			if(!success) {
				System.out.println("Error with task " + t.toString());
			}
		}
	}
	
	// Return the cost of this solution.
	public double getCost() {
		double cost = 0;
		
		for(Map.Entry<Vehicle, TaskList> entry : tasksPerVehicle.entrySet()) {
			Vehicle v = entry.getKey();
			TaskList taskList = entry.getValue();
			
			cost += v.costPerKm()*taskList.getDistance(v.homeCity());
		}
		
		return cost;
	}
	
	// Return the plan of each vehicle corresponding to this solution.
	public Map<Vehicle, Plan> getPlans() {
		Map<Vehicle, Plan> plans = new HashMap<Vehicle, Plan>();
		
		for(Map.Entry<Vehicle, TaskList> entry : tasksPerVehicle.entrySet()) {
			Vehicle v = entry.getKey();
			TaskList taskList = entry.getValue();
			
			Plan plan = taskList.getPlan(v.getCurrentCity());
			plans.put(v, plan);
		}
		
		return plans;
	}
	
	// Return a string describing the solution
	public String toString() {
		String msg = "Total cost : " + this.getCost() + "\n";
		
		for(Map.Entry<Vehicle, TaskList> entry : tasksPerVehicle.entrySet()) {
			Vehicle v = entry.getKey();
			TaskList taskList = entry.getValue();
			msg += "Vehicle " + v.id() + " : ";
			for(TaskAction a : taskList.actions) {
				msg += a.isPickUp ? "Pickup " : "Deliver ";
				msg += a.task.task.id + ", ";
			}
			msg += "\n";
		}
		
		return msg;
	}
	
	
	
	
	
	// Class representing a list of tasks and the order in which they are picked up
	// and delivered.
	private class TaskList {
		public List<OrderedTask> tasks; // List of tasks
		public List<TaskAction> actions; // List of pickup and delivery actions
		
		public TaskList(List<OrderedTask> tasks) {
			this.tasks = new ArrayList<OrderedTask>(tasks);
			this.actions = new ArrayList<TaskAction>();
			for(int i = 0; i < 2*tasks.size(); i++)
				this.actions.add(null);
			
			for(OrderedTask t : tasks) {
				TaskAction pickUp = new TaskAction(t, true);
				TaskAction delivery = new TaskAction(t, false);
				
				this.actions.set(t.pickUpOrder, pickUp);
				this.actions.set(t.deliverOrder, delivery);
			}
		}
		
		// Create a deep copy
		public TaskList(TaskList taskList) {
			this.tasks = new ArrayList<OrderedTask>();
			
			// Create a deep copy of each ordered task and add them to the new TaskList
			for(OrderedTask t : taskList.tasks) {
				this.tasks.add(new OrderedTask(t));
			}

			this.actions = new ArrayList<TaskAction>();
			// Prefill the actions list
			for(int i = 0; i < 2*tasks.size(); i++)
				this.actions.add(null);
			
			// Create pickup and delivery actions for each task and add them to the actions list
			for(OrderedTask t : tasks) {
				TaskAction pickUp = new TaskAction(t, true);
				TaskAction delivery = new TaskAction(t, false);
				
				this.actions.set(t.pickUpOrder, pickUp);
				this.actions.set(t.deliverOrder, delivery);
			}
		}
		
		// Get all possible permutations of one task (both pickup and delivery)
		public List<TaskList> getAllPermutations(int vehicleCapacity, int taskIdx) {
			TaskList permuted = new TaskList(this);
			
			Task task = permuted.removeTask(taskIdx);
			
			return permuted.getAllInsertions(vehicleCapacity, task);
		}
		
		// Get all possible insertions of a task.
		public List<TaskList> getAllInsertions(int vehicleCapacity, Task task) {
			List<TaskList> insertions = new ArrayList<TaskList>();
			TaskList insertion;
			
			for(int i = 0; i < actions.size() + 1; i++) {
				for(int j = i + 1; j < actions.size() + 2; j++) {
					insertion = new TaskList(this);
					
					insertion.insertTask(task, i, j);
					
					if(insertion.checkWeights(vehicleCapacity))
						insertions.add(insertion);
				}
			}
			
			return insertions;
		}
		
		// Check if the actions are feasible with the given capacity
		public boolean checkWeights(int vehicleCapacity) {
			int weight = 0;
			
			for(TaskAction a : actions) {
				weight += a.isPickUp ? a.task.task.weight : -a.task.task.weight;
				if(weight > vehicleCapacity)
					return false;
			}
			
			return true;
		}
		
		// Insert the given task with the given pickup and delivery order
		public void insertTask(Task task, int pickUpOrder, int deliverOrder) {
			OrderedTask oTask = new OrderedTask(task, pickUpOrder, deliverOrder);
			TaskAction pickUp = new TaskAction(oTask, true);
			TaskAction deliver = new TaskAction(oTask, false);
			
			this.tasks.add(oTask);
			this.actions.add(pickUpOrder, pickUp);
			this.actions.add(deliverOrder, deliver);
			
			for(int i = pickUpOrder + 1; i < actions.size(); i++) {
				this.actions.get(i).setOrder(i);
			}
		}
		
		// Remove the idx-th task from the list.
		public Task removeTask(int idx) {			
			OrderedTask task = this.tasks.remove(idx);
			this.actions.remove(task.deliverOrder);
			this.actions.remove(task.pickUpOrder);
			
			for(int i = task.pickUpOrder; i < actions.size(); i++) {
				this.actions.get(i).setOrder(i);
			}
			
			return task.task;
		}
		
		// Return the distance to pick up and deliver all tasks, starting in the
		// provided city
		public double getDistance(City currentCity) {
			double dist = 0;
			
			City lastCity = currentCity;
			for(TaskAction a : actions) {
				dist += a.getCity().distanceTo(lastCity);
				lastCity = a.getCity();
			}
			
			return dist;
		}
		
		// Return the corresponding plan.
		public Plan getPlan(City initCity) {
			Plan plan = new Plan(initCity);
			
			City lastCity = initCity;
			for(TaskAction a : this.actions) {
				List<City> path = lastCity.pathTo(a.getCity());
				
				for(City c : path) {
					plan.appendMove(c);
				}
				
				if(a.isPickUp) {
					plan.appendPickup(a.task.task);
				}
				else {
					plan.appendDelivery(a.task.task);
				}
				
				lastCity = a.getCity();
			}
			
			return plan;
		}
	}
	
	
	
	
	
	// Class representing a task and the indexes at which it is picked up and delivered.
	private class OrderedTask {
		public final Task task;
		public int pickUpOrder;
		public int deliverOrder;
		
		public OrderedTask(Task task, int pickUpOrder, int deliverOrder) {
			this.task = task;
			this.pickUpOrder = pickUpOrder;
			this.deliverOrder = deliverOrder;
		}
		
		// Deep copy
		public OrderedTask(OrderedTask task) {
			this(task.task, task.pickUpOrder, task.deliverOrder);
		}
	}	
	
	
	
	
	
	// Class representing an action for a task, i.e. pick it up or deliver it.
	private class TaskAction {
		public final OrderedTask task;
		public final boolean isPickUp;
		
		public TaskAction(OrderedTask task, boolean isPickUp) {
			this.task = task;
			this.isPickUp = isPickUp;
		}
		
		// Set the order of the action in its list.
		public void setOrder(int i) {
			if(isPickUp) {
				this.task.pickUpOrder = i;
			}
			else {
				this.task.deliverOrder = i;
			}
		}
		
		// Get the city in which the action must be performed.
		public City getCity() {
			if(isPickUp) {
				return task.task.pickupCity;
			}
			else {
				return task.task.deliveryCity;
			}
		}
	}
}