package template;

import java.io.File;
//the list of imports
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import logist.LogistSettings;
import logist.agent.Agent;
import logist.behavior.AuctionBehavior;
import logist.config.Parsers;
import logist.plan.Action;
import logist.plan.Plan;
import logist.simulation.Vehicle;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.task.TaskSet;
import logist.topology.Topology;
import logist.topology.Topology.City;

/**
 * A very simple auction agent that assigns all tasks to its first vehicle and
 * handles them sequentially.
 * 
 */
@SuppressWarnings("unused")
public class AuctionBAndY implements AuctionBehavior {
	private Topology topology;
	private TaskDistribution distribution;
	private Agent agent;
	private long timeout_setup, timeout_plan, timeout_bid;
	private finalState agentPlan;
	private finalState tempPlan;
	private ArrayList<Task> memoryTaskAuction;
	private ArrayList<Integer> memoryWinnerAuction;
	private ArrayList<ArrayList<Long>> memoryBidsAuction;
	private double futurInAccount = 0.9;
	private double futurPower = 1;
	private double futurRatio = 0.8;
	private double futurTime = futurInAccount/5;
	private double ratio = 1;
	private ArrayList<ArrayList<City>> memoryPresence;
	private ArrayList<City> presenceAgentAlly;
	private ArrayList<City> presenceAgentAllyTemp;
	private double rapportDistToBid = 3;
	private int numberOfCitiesBestTask;

	// Probability for localChoice()
	double probability;
	// Counters of iterations
	int maxCounterSinceNewSolution, maxIteration;

	@Override
	public void setup(Topology topology, TaskDistribution distribution, Agent agent) {
		// this code is used to get the timeouts
		LogistSettings ls = null;
		try {
			ls = Parsers.parseSettings("config" + File.separator + "settings_auction.xml");
		} catch (Exception exc) {
			System.out.println("There was a problem loading the configuration file.");
		}

		// the setup method cannot last more than timeout_setup milliseconds
		timeout_setup = ls.get(LogistSettings.TimeoutKey.SETUP);
		// the plan method cannot execute more than timeout_plan milliseconds
		timeout_plan = ls.get(LogistSettings.TimeoutKey.PLAN);
		// the bid method cannot execute more than timeout_bid milliseconds
		timeout_bid = ls.get(LogistSettings.TimeoutKey.BID);

		this.topology = topology;
		this.distribution = distribution;
		this.agent = agent;
		
		System.out.println("Start setup");

		memoryTaskAuction = new ArrayList<Task>();
		memoryWinnerAuction = new ArrayList<Integer>();
		memoryBidsAuction = new ArrayList<ArrayList<Long>>();
		
		memoryPresence = new ArrayList<ArrayList<City>>();
		
		presenceAgentAlly = new ArrayList<City>();
		
		for (int i = 0; i<2; i++) {
			memoryPresence.add(new ArrayList<City>());
			memoryBidsAuction.add(new ArrayList<Long>());
		}

		// Get the parameters of the algorithm from the agent.xml file
		probability = agent.readProperty("probability", Double.class, 0.3);
		maxCounterSinceNewSolution = agent.readProperty("maxCounterSinceNewSolution", Integer.class, 10000);
		maxIteration = agent.readProperty("maxIteration", Integer.class, 100 * 1000);

		int numberOfCities;
		
//		for(City city : topology.cities())
//			for(City city2nd : topology.cities()) {
//				numberOfCities = city.pathTo(city2nd).size() + 1;
//				if(numberOfCities > numberOfCitiesBestTask)
//					numberOfCitiesBestTask = numberOfCities;
//			}
		
		List<City> tempList, tempList2;
		
//		for(City city : topology.cities())
//			for(City city2nd : topology.cities()) {				
//				for(City city3nd : topology.cities()) {
//					tempList = city.pathTo(city2nd);
//					tempList.add(city);
//					
//					for(City city4th : city2nd.pathTo(city3nd))
//						if(!tempList.contains(city4th))
//							tempList.add(city4th);
//					
//					numberOfCities = tempList.size();
//					
//					if(numberOfCities > numberOfCitiesBestTask)
//						numberOfCitiesBestTask = numberOfCities;
//				}
//			}
		
		numberOfCitiesBestTask = topology.cities().size();
		
		System.out.println("Finished setup");
	}

	// A class of the final state = goal state
    private class finalState implements Comparable<finalState>{
    	// list of size vehicle containing list of custom tasks
    	private ArrayList<ArrayList<customTask>> cTaskByVehicleList;
    	
    	// TotalCost of the plan depending of this final state
    	double totalCost = 0;

		// Constructor of a new State
		public finalState(ArrayList<ArrayList<customTask>> cTaskByVehicleList, List<Vehicle> vehicles, TaskSet tasks){
			this.cTaskByVehicleList = cTaskByVehicleList;
	    	computeTotalCost(vehicles, tasks);
		}
		
		// Constructor of a copy of a finalState
		public finalState(finalState another) {
			this.cTaskByVehicleList = new ArrayList<ArrayList<customTask>>();
			for(ArrayList<customTask> sublist : another.getCTaskByVehicleList()) {
				this.cTaskByVehicleList.add(new ArrayList<customTask>(sublist));
			}
		    this.totalCost = another.getTotalCost();
		    }
		
		// Compute the totalCost of the plan depending of this final state
		public void computeTotalCost(List<Vehicle> vehicles, TaskSet tasks) {
			double sumDist;
			totalCost = 0;
			int indexVehi = 0;
			City currentCity, nextCity;
			ArrayList<customTask> cTaskList;
			
			for (Vehicle vehi:vehicles){
				sumDist = 0;
				
				cTaskList = cTaskByVehicleList.get(indexVehi); // List of task of this vehi
				currentCity = vehi.getCurrentCity(); // starting city of this vehi
				
				for(customTask cTask : cTaskList) {
					if(!cTask.getPickOrDeliv())
						nextCity = cTask.getTask().pickupCity;
					else
						nextCity = cTask.getTask().deliveryCity;
					
					sumDist = sumDist + currentCity.distanceTo(nextCity);
					currentCity = nextCity;
				}
				
				totalCost = totalCost + (double) Math.round(sumDist * vehi.costPerKm());
				indexVehi ++;
			}
		}
		
		@Override
	    public boolean equals(Object o) {     
          if (o instanceof finalState){
        	  	finalState fS = (finalState) o;
        	  
        	  	if(fS.getCTaskByVehicleList().equals(this.getCTaskByVehicleList()))
        	  		return true;
          }    
          return false;
	    }
		
		public void replaceTask(Task realTask) {
			for (ArrayList<customTask> cTaskList : cTaskByVehicleList){
				for(customTask cTask : cTaskList) {
					if(cTask.getTask().id == realTask.id)
						cTask.setTask(realTask);
				}
			}
		}
		
		@Override // Function used by List.sort overrided to correspond to our needs
	    public int compareTo(finalState fS) {return this.totalCost < fS.totalCost ? -1 : this.totalCost == fS.totalCost ? 0 : 1;}
		
		// Get functions
		public ArrayList<ArrayList<customTask>> getCTaskByVehicleList() {return cTaskByVehicleList;}

		public double getTotalCost() {return totalCost;}
    }

	private class customTask{
    	private Task task;
		private Boolean pickOrDeliv;
    	
    	public customTask(Task task, Boolean pickOrDeliv) {
    		this.task = task;
    		this.pickOrDeliv = pickOrDeliv;
    	}
    	
    	@Override
	    public boolean equals(Object o) {     
          if (o instanceof customTask){
        	  customTask cT = (customTask) o;
        	  
        	  if(cT.getTask() == this.getTask())
        		  return true;     		 
          }    
          return false;
	    }
    	
    	// Get functions
    	public Task getTask() {return task;}
    	public void setTask(Task newTask) {task = newTask;}
		public Boolean getPickOrDeliv() {return pickOrDeliv;}
    }

	@Override
	public void auctionResult(Task previous, int winner, Long[] bids) {
		int indexAgent = 0;
		
		memoryTaskAuction.add(previous);
		memoryWinnerAuction.add(winner);
		
		for (Long price : bids) {
			memoryBidsAuction.get(indexAgent).add(price);
			indexAgent ++ ;
		}
		
		// Updates the presence of this agent on the map
		ArrayList<City> thisAgentCities = memoryPresence.get(winner);
		
		if(!thisAgentCities.contains(previous.pickupCity))
			thisAgentCities.add(previous.pickupCity);
		for(City city : previous.path()) {
			if(!thisAgentCities.contains(city))
				thisAgentCities.add(city);
		}
		
		if (winner == agent.id()) {
			
//			for(int i = 0; i < futurPower; i++) {
//				futurInAccount = futurRatio * futurInAccount;
//			}
			
			presenceAgentAlly = presenceAgentAllyTemp;
			
			for(int i = 0; i < futurPower; i++) {
				futurInAccount = futurInAccount - futurTime ;
			}
			if (futurInAccount < 0)
				futurInAccount = 0;
			
			agentPlan = tempPlan;
			agentPlan.replaceTask(previous);
		}

		System.out.print("Task " + previous + " has be won by " + winner + " with cost: ");
		for (Long price : bids)
			System.out.print(price + " ");
		System.out.print("\n");
	}

	@Override
	public Long askPrice(Task task) {
		double bid, marginalCost;
		finalState nowTempPlanWithNewTask;
		
		List<Vehicle> vehicles = agent.vehicles();
		TaskSet tasks = agent.getTasks().clone();
//		System.out.print("test " + tasks.size() + " ");
		tasks.add(task);
		tempPlan = findBestTempPlan(vehicles, tasks, task);
		
		if (tempPlan == null)
			return null;

		if (agentPlan == null)
			marginalCost = tempPlan.getTotalCost();
		else
			marginalCost = tempPlan.getTotalCost() - agentPlan.getTotalCost();
		
		bid = marginalCost;
		
		bid = ratio * bid;
		
		if(futurInAccount != 0) {
			bid = bidFromFutur(task, bid);
		}
		
		bid = bidFromAgents(task, bid, marginalCost);
		
		return (long) Math.round(bid);
	}
	
	public double bidFromAgents(Task previous, double bid, double marginalCost) {
		// bis sur les 5 dernières mises ?
		
		// bids en connaissant les taches prise par les autres => connais sa présence sur la carte
		double minBid = -1;
		double newBid;
		int indexAgent = 0;
		double dist, tempDist;
		double minDistEnnemyAgent = -1;
		double minDistOurAgent = -1;
		boolean tempAddCity;
		ArrayList<Double> distanceThisAgentToTask = new ArrayList<Double>();
		ArrayList<Boolean> pickupCityOnThisAgentPath = new ArrayList<Boolean>();
		ArrayList<Boolean> deliverCityOnThisAgentPath = new ArrayList<Boolean>();
		
		if(previous.id > 0) {
			for(ArrayList<City> presenceThisAgent : memoryPresence) {
				dist = 0;
				tempDist = 0;
				tempAddCity = false;
				
				if(presenceThisAgent.contains(previous.pickupCity))
					pickupCityOnThisAgentPath.add(true);
				else {
					pickupCityOnThisAgentPath.add(false);
					for(City city : presenceThisAgent) {
						if(tempDist == 0) {
							tempDist = city.distanceTo(previous.pickupCity);
						}
						else if(city.distanceTo(previous.pickupCity) < tempDist)
							tempDist = city.distanceTo(previous.pickupCity);
					}
					tempAddCity = true;
					presenceThisAgent.add(previous.pickupCity);
					
					dist = dist + tempDist;
				}
				
				if(presenceThisAgent.contains(previous.deliveryCity))
					deliverCityOnThisAgentPath.add(true);
				else {
					deliverCityOnThisAgentPath.add(false);
					for(City city : presenceThisAgent) {
						if(tempDist == 0) {
							tempDist = city.distanceTo(previous.deliveryCity);
						}
						else if(city.distanceTo(previous.deliveryCity) < tempDist)
							tempDist = city.distanceTo(previous.deliveryCity);
					}
					
					dist = dist + tempDist;
				}
				if(tempAddCity)
					presenceThisAgent.remove(previous.pickupCity);
				
				distanceThisAgentToTask.add(dist);
				
				if(indexAgent == agent.id()){
					if(minDistOurAgent < 0)
						minDistOurAgent = dist;
					else if(dist < minDistOurAgent)
						minDistOurAgent = dist;
				}
				else {
					if(minDistEnnemyAgent < 0)
						minDistEnnemyAgent = dist;
					else if(dist < minDistEnnemyAgent)
						minDistEnnemyAgent = dist;
				}
				
				indexAgent ++ ;
			}
			
			System.out.println("Agent 0 " + distanceThisAgentToTask.get(0) + " Agent 1 " + distanceThisAgentToTask.get(1));
		}
		
		indexAgent = 0;
		
		for(ArrayList<Long> priceList : memoryBidsAuction) {
			if(indexAgent != agent.id())
				for(Long price : priceList) {
					if(minBid < 0)
						minBid = price;
					else
						if(price < minBid)
							minBid = price;
				}
			indexAgent ++ ;
		}
		
		newBid = bid;
		
		double test = newBid/rapportDistToBid;
		double deltaDist = minDistEnnemyAgent - minDistOurAgent;
		//deltaDist = minDistEnnemyAgent - test;
		
		double potNewBid;
		
		if(futurInAccount == 0) {
			if (deltaDist > 0) {
				potNewBid = deltaDist * rapportDistToBid;
				if(potNewBid > newBid) {
					System.out.println(" + NewBid " + potNewBid + " instead of " + newBid);
					newBid = potNewBid;
				}
			}
		}
		
		// min
		if(newBid < 0.9 * minBid) {
			System.out.println(" - bid " + 0.9 * minBid + " instead of " + newBid);
			newBid = 0.9 * minBid;
		}
		
//		if(minDistEnnemyAgent >= test) {
//		System.out.println(" + New newBid " + newBid * minDistEnnemyAgent/test + " instead of " + newBid);
//		newBid = newBid * minDistEnnemyAgent/test;
//		}
		
//		if(newBid < 0.9 * minBid) {
//			System.out.println(" - bid " + 0.9 * minBid + " instead of " + newBid);
//			newBid = 0.9 * minBid;
//		}
		
		return newBid;
	}
	
	public double bidFromFutur(Task previous, double bid) {
		double newBid;
		
		// greed for task with lot of cities
		// Updates the presence of this agent on the map
		ArrayList<City> thisAgentCities = memoryPresence.get(agent.id());
		int counterOfCities = 0;
		
		if(!thisAgentCities.contains(previous.pickupCity)) {
			thisAgentCities.add(previous.pickupCity);
			counterOfCities++;
		}
		for(City city : previous.path()) {
			if(!thisAgentCities.contains(city)) {
				thisAgentCities.add(city);
				counterOfCities++;
			}
		}
		
		double ratioNumberOfCities = counterOfCities/numberOfCitiesBestTask;
		
//		printn(presenceAgentAllyTemp.size());
//		printn(presenceAgentAlly.size());
//		printn(numberOfCitiesBestTask);
		
		ratioNumberOfCities = (presenceAgentAllyTemp.size() - presenceAgentAlly.size()) / numberOfCitiesBestTask;
		
		newBid = bid * (1 - ratioNumberOfCities);
		
		newBid = newBid * (1 - futurInAccount);
		
		return newBid;
	}
	
	public finalState findBestTempPlan(List<Vehicle> vehicles, TaskSet tasks, Task potNewTask) {
		long time_start = System.currentTimeMillis();
        
        List<Plan> plans = new ArrayList<Plan>();
        List<finalState> NList = new ArrayList<finalState>();
        finalState A, AOld, ABest;
        int counterIteration, counterSinceNewSolution;
        
        // Initialization
        counterIteration = counterSinceNewSolution = 0;
        
        // SLS algorithm for COP
        A = selectInitialSolution(vehicles, tasks, potNewTask);
        
        // Catch error
        if (A == null) {
        	System.out.println("Problem unsolvalbe : at least one task has a bigger weight than the capacity of the biggest vehicule");
        	return A;
        }
        
        // Memories
        ABest = new finalState(A);
		
        while(true){
        	AOld = new finalState(A);
        	
//        	for(ArrayList<customTask> tasksThisVehicle : AOld.getCTaskByVehicleList())
//        		System.out.print(tasksThisVehicle.size() + " ");
//        	System.out.println(" ");
        	
        	// Create a list of neighbor final states
        	NList = chooseNeighbours(AOld, vehicles, tasks);
        	
        	A = localChoice(NList);
        	
        	// Saves the best A ever and a best temporary A
    		if(A.getTotalCost() < ABest.getTotalCost()) {
        		ABest = A;
        		counterSinceNewSolution = 0;
        	}
        	
//        	System.out.println("with totalCost = " + A.totalCost + " with itera = " + counterSinceNewSolution);
        	
        	counterIteration ++ ;
        	counterSinceNewSolution ++ ;
        	
        	// Stopping criterion = max iteration reached or if best A ever hasn t changed since too long
        	if(counterIteration >= maxIteration || counterSinceNewSolution >= maxCounterSinceNewSolution)
        		break;
        }
        
//      long time_end = System.currentTimeMillis();
//      long duration = time_end - time_start;
//      System.out.println("The plan was generated in " + duration + " milliseconds.");
//      System.out.println("The plan was generated with " + counterIteration + " iterations, last best A hasn't changed for " + counterSinceNewSolution + " iterations and has a totalCost = " + ABest.totalCost);

//        for(ArrayList<customTask> tasksThisVehicle : AOld.getCTaskByVehicleList())
//        	System.out.print(tasksThisVehicle.size() + " ");
//    	System.out.println(" ");
        
    	ArrayList<customTask> cTaskList;
    	ArrayList<Action> actionList;
    	City currentCity, nextCity;
		int indexVehi = 0;
		
		presenceAgentAllyTemp = new ArrayList<City>();

		for (Vehicle vehi : vehicles){
    		
    		presenceAgentAllyTemp.add(vehi.getCurrentCity());
			
			cTaskList = ABest.getCTaskByVehicleList().get(indexVehi); // List of task of this vehi
			currentCity = vehi.getCurrentCity(); // starting city of this vehi
			
			for(customTask cTask : cTaskList) {
				if (!cTask.getPickOrDeliv()) {
					nextCity = cTask.getTask().pickupCity;
    				
    				for (City city : currentCity.pathTo(nextCity))
    					if(!presenceAgentAllyTemp.contains(city))
    						presenceAgentAllyTemp.add(city);
    			}
				else {
					nextCity = cTask.getTask().deliveryCity;
					
					for (City city : currentCity.pathTo(nextCity))
    					if(!presenceAgentAllyTemp.contains(city))
    						presenceAgentAllyTemp.add(city);
				}
				currentCity = nextCity;
			}
			
			indexVehi ++;
		}
        
        return ABest;
    }

	@Override
	public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks) {
		List<Plan> plans = new ArrayList<Plan>();
    	ArrayList<customTask> cTaskList;
    	ArrayList<Action> actionList;
    	City currentCity, nextCity;
    	Plan plan;
		int indexVehi = 0;
		
		presenceAgentAllyTemp = new ArrayList<City>();

		for (Vehicle vehi : vehicles){
			plan = Plan.EMPTY;
    		actionList = new ArrayList<Action>();
    		
    		presenceAgentAllyTemp.add(vehi.getCurrentCity());
			
			cTaskList = agentPlan.getCTaskByVehicleList().get(indexVehi); // List of task of this vehi
			currentCity = vehi.getCurrentCity(); // starting city of this vehi
			
			for(customTask cTask : cTaskList) {
				if (!cTask.getPickOrDeliv()) {
					nextCity = cTask.getTask().pickupCity;
    				
    				// Add action(s) to the action list to move: current city => nextCity
    				for (City city : currentCity.pathTo(nextCity)) {
    					actionList.add(new Action.Move(city));
    					if(!presenceAgentAllyTemp.contains(city))
    						presenceAgentAllyTemp.add(city);
    				}
    				
    				// Add the action to pickup this task
    				actionList.add(new Action.Pickup(cTask.getTask()));
    			}
				else {
					nextCity = cTask.getTask().deliveryCity;
					
					// Add action(s) to the action list to move: current city => nextCity
					for (City city : currentCity.pathTo(nextCity)) {
    					actionList.add(new Action.Move(city));
    					if(!presenceAgentAllyTemp.contains(city))
    						presenceAgentAllyTemp.add(city);
    				}
					
					// Add the action to deliver this task
					actionList.add(new Action.Delivery(cTask.getTask()));
				}
				currentCity = nextCity;
			}
			plan = new Plan(vehi.getCurrentCity(), actionList);
    		plans.add(plan);
			
			indexVehi ++;
		}
		
        return plans;
	}

	// Create an initial A by asking the vehicle with the biggest capacity to handle all the tasks
    private finalState selectInitialSolution(List<Vehicle> vehicles, TaskSet tasks, Task potNewTask) {
    	ArrayList<ArrayList<customTask>> cTaskByVehicleList = new ArrayList<ArrayList<customTask>>();
    	int maxCapacity, indexVehiMaxCapacity, indexVehi;
    	maxCapacity = indexVehiMaxCapacity = indexVehi = 0;
    	customTask cTask;
    	finalState A;
    	
    	// Search the vehicle with the biggest capacity
    	for(Vehicle vehi:vehicles) {
    		if(vehi.capacity() > maxCapacity) {
    			maxCapacity = vehi.capacity();
    			indexVehiMaxCapacity = indexVehi;
    		}
    		cTaskByVehicleList.add(new ArrayList<customTask>());
    		indexVehi ++;
    	}
    	
    	if(agentPlan != null) {
    		A = new finalState(agentPlan);
    		
    		cTask = new customTask(potNewTask, false);
    		A.getCTaskByVehicleList().get(indexVehiMaxCapacity).add(cTask);
    		
    		cTask = new customTask(potNewTask, true);
    		A.getCTaskByVehicleList().get(indexVehiMaxCapacity).add(cTask);
    		A.computeTotalCost(vehicles, tasks);
    		return A;
    	}

    	// Create nextTasktList, nextTaskvList and timeList
    	for(Task task:tasks) {
    		// Checking if problem is solvable or not
    		if (task.weight > maxCapacity) {return null;} 
    		
    		cTask = new customTask(task, false);
    		cTaskByVehicleList.get(indexVehiMaxCapacity).add(cTask);
    		
    		cTask = new customTask(task, true);
    		cTaskByVehicleList.get(indexVehiMaxCapacity).add(cTask);
    	}
    	
    	// Create the new final state
    	A = new finalState(cTaskByVehicleList, vehicles, tasks);
//    	System.out.println("Initial A with totalCost = " + A.totalCost);
    	return A;
    }

	// Choose neighbours finals states of a given final state 
    private List<finalState> chooseNeighbours (finalState AOld, List<Vehicle> vehicles, TaskSet tasks){
    	ArrayList<ArrayList<customTask>> cTaskByVehicleListTemp, cTaskByVehicleListNew;
    	ArrayList<finalState> NList = new ArrayList<finalState>();
    	customTask randomCTaskPick = null, randomCTaskDeliver = null;
    	finalState ANew, ATemp;
    	int sizeCTaskList, indexVehi, indexTask;
    	sizeCTaskList = indexVehi = indexTask = 0;
    	int randomIdTask = (int) Math.floor(Math.random()*tasks.size());
    	
    	for(Task task : tasks) {
    		if(indexTask == randomIdTask) {
    			randomCTaskPick = new customTask(task, false);
    			randomCTaskDeliver = new customTask(task, true);
    		}
    		indexTask ++;
    	}
    	
    	ATemp = new finalState(AOld);
    	
    	cTaskByVehicleListTemp = ATemp.getCTaskByVehicleList();
    	
    	for(ArrayList<customTask> cTaskList : cTaskByVehicleListTemp) {
    		cTaskList.remove(randomCTaskPick);
			cTaskList.remove(randomCTaskDeliver);
    	}

    	indexVehi = 0;
    	for(ArrayList<customTask> cTaskList : cTaskByVehicleListTemp) {
    		sizeCTaskList = cTaskList.size();
    		for(int indexCTaskPick = 0; indexCTaskPick <= sizeCTaskList; indexCTaskPick ++) {
    			for(int indexCTaskDeliver = indexCTaskPick + 1; indexCTaskDeliver <= sizeCTaskList+1; indexCTaskDeliver ++) {
    				cTaskList.add(indexCTaskPick, randomCTaskPick);
    				cTaskList.add(indexCTaskDeliver, randomCTaskDeliver);
    				ANew = new finalState(ATemp);
    				if(isInsertionPossible(cTaskList, indexVehi, vehicles.get(indexVehi))) {
    					ANew.computeTotalCost(vehicles, tasks);
    					NList.add(ANew);
    				}
					cTaskList.remove(randomCTaskDeliver);
					cTaskList.remove(randomCTaskPick);
        		}
    		}
    		indexVehi ++;
    	}
    	
    	NList.remove(AOld);
    	
    	return NList;
    }

	// Function to check if state ANew is achievable
    private boolean isInsertionPossible(ArrayList<customTask> cTaskList, int indexVehi, Vehicle vehicle) {    	
    	int freeLoadVehicle = vehicle.capacity();
    	
		for(customTask cTask : cTaskList) {
			if(!cTask.getPickOrDeliv())
				freeLoadVehicle -= cTask.getTask().weight;
			else
				freeLoadVehicle += cTask.getTask().weight;
			
			if(freeLoadVehicle < 0)
    			return false;
		}
    	
    	return true;
    }

	// Choose a final state in a List of final states
    private finalState localChoice(List<finalState> NList) {
    	finalState A, ARandom;
    	double random = Math.random();
    	
    	// If we want to go out of a local minima, assign a random neighbor, else assign the best one
		NList.sort(null);
		A = NList.get(0);

		Collections.shuffle(NList);
		ARandom = NList.get(0);

		if(random <= probability)
	    	return A;
		else
			return ARandom;
    }
    
    private void print(Object o) {
		System.out.print(o);
	}
    
    private void printn(Object o) {
		System.out.println(o);
	}
}