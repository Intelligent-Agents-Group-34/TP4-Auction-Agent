package template;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.swing.JFrame;

import logist.agent.Agent;
import logist.behavior.AuctionBehavior;
import logist.plan.Action;
import logist.plan.Plan;
import logist.simulation.Vehicle;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.task.TaskSet;
import logist.topology.Topology;
import logist.topology.Topology.City;
import ptolemy.plot.Plot;

public class AuctionAgent implements AuctionBehavior {

	private Agent agent;
	private Topology topology;
	private TaskDistribution distribution;
	private double cost;
	private double newCost;
	private Solution solution;
	private Solution newSolution;
	private List<Double> meanCostPerTask;
	private Task lastTask;
	
	private List<Double> ennemyBids;
	private List<Task> ennemyTasks;

	@Override
	public void setup(Topology topology, TaskDistribution distribution,
			Agent agent) {
		this.agent = agent;
		this.topology = topology;
		this.distribution = distribution;
		this.cost = 0;
		this.newCost = 0;
		this.solution = this.getInitialSolution(agent.vehicles(), TaskSet.create(new Task[0]));
		this.meanCostPerTask = new ArrayList<Double>();
		this.newSolution = solution;
		
		this.ennemyBids = new ArrayList<Double>();
		this.ennemyTasks = new ArrayList<Task>();
		
//		this.computeMeanCostPerTask();
	}
	
	@Override
	public Long askPrice(Task task) {
		this.lastTask = task;
		
		this.newSolution = this.computePlan(solution, task);
		
		if(newSolution == null)
			return null;

		this.newCost = newSolution.getCost();
		
		if(newCost < cost) {
			this.solution = new Solution(newSolution);
			this.solution.removeTask(task);
			this.cost = solution.getCost();
		}

		double Cm = newCost - cost;
		
		int n = agent.getTotalTasks(); // Number of tasks we currently have
		int h = 5; // Horizon
//		double gamma = ((double)n_bar + 3)/((double)n + 3); // Importance given to the future
//		double gamma = 1. - 2.*(n - n_bar)/h; // How much we care about the future
//		double epsilon = 2.*(n - n_bar)/h; // How much we care about the goodness of the task compared to the mean
		double gamma = 1. - 2.*(n - 0)/h; // How much we care about the future
		double epsilon = 2.*(n - 0)/h; // How much we care about the goodness of the task compared to the mean
		
//		double Ch = this.getMeanFutureCost(tasks, h - 1, 10)/(n + h - 1);
		double Ch = this.getMeanFutureCost(agent.getTasks(), h, 10)/(n + h);
		double Cn = this.getMeanFutureCost(agent.getTasks(), 1, 10)/(n + 1);
		double C;
		if(Cm > Cn)
			C = Cn + Math.max(epsilon, 0.)*(Cm - Cn);
		else
			C = Cn + Math.max(1. - epsilon, 0.)*(Cm - Cn);
		
		System.out.println("Tasks: " + n + " / " + ennemyTasks.size());
		System.out.println("Marginal Cost: " + Cm);
		System.out.println("C(n+h): " + Ch);
		System.out.println("C(n): " + Cn);
		System.out.println("C: " + C);
		
		double bid;
		double k = 8, l = 1/0.8, aggressivity = 4;
		
		double meanEnnemyBid = 0;
		double minEnnemyBid = Double.POSITIVE_INFINITY;
		int ennemyNB = Math.min(ennemyBids.size(), h);
		if(ennemyNB == 0)
			minEnnemyBid = 0;
		else {
			for(int i = 0; i < ennemyNB; i++) {
				meanEnnemyBid += ennemyBids.get(ennemyBids.size() - 1 - i);
				minEnnemyBid = Math.min(minEnnemyBid, ennemyBids.get(ennemyBids.size() - 1 - i));
			}
			meanEnnemyBid /= ennemyNB;
		}
		
		System.out.println("Ennemy min bid: " + minEnnemyBid);
		
		C = Cm/Cn*Ch;
		System.out.println("Ratio: " + (Ch/Cn));
//		if(n < h) {
//			double fact1 = Math.pow((double)n/h, 1/aggressivity);
//			double fact2 = Math.pow((double)n/h, aggressivity);
//			bid = C/(k + (1 - k)*fact1) + fact1*Cn/l;
//		}
		if(n < 3) {
			bid = 0;
		}
		else {
//			bid = C + Cn/l;
			bid = Math.max(C, 0.8*meanEnnemyBid);
		}
		
		bid = Math.max(bid, 0.8*minEnnemyBid);
		
		
		System.out.println("Bid: " + bid + "\n");
		return (long)bid;
	}
	
	@Override
	public void auctionResult(Task task, int winner, Long[] offers) {
		if(winner == agent.id()) {
			Solution sol = this.computePlan(agent.vehicles(), agent.getTasks());
			double cost = sol.getCost();
			
			if(cost < this.newCost) {
				this.solution = sol;
				this.cost = cost;
			}
			else {
				this.solution = newSolution;
				this.solution.updateTask(lastTask, task);
				this.cost = newCost;
			}
		}
		else {
			this.ennemyTasks.add(task);
		}
		
		int ennemyAgentID = 1 - agent.id();
		this.ennemyBids.add((double)offers[ennemyAgentID]);
	}
	
    @Override
    public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks) {
        long time_start = System.currentTimeMillis();
        
        List<Plan> plans = new ArrayList<Plan>();
        
        // Compute a good plan with the SLS algorithm
//        Solution sol = this.computePlan(vehicles, tasks);
        Solution sol = solution;
        
        if(sol == null) {
            for(Vehicle v : vehicles) {
            	plans.add(Plan.EMPTY);
            }
        }
        else {
//            sol.updateTasks(tasks);
	        Map<Vehicle, Plan> planMap = sol.getPlans();
	        
	        for(Vehicle v : vehicles) {
	        	Plan plan = planMap.get(v);
	        	plans.add(plan);
	        	
	        	String msg = "Vehicle " + v.id() + " with capacity " + v.capacity()
	        			+ " has plan:\n" + plan.toString() + "\n";
	        	System.out.println(msg);
	        }
        }
        
        long time_end = System.currentTimeMillis();
        long duration = time_end - time_start;
        System.out.println("The plan was generated in " + duration + " milliseconds.");
        
        return plans;
    }
    
    private Task getRandomTask(int id) {
    	double p = (new Random()).nextDouble();
    	double cumul = 0;
    	
    	for(City c1 : topology.cities()) {
    		for(City c2 : topology.cities()) {
    			cumul += distribution.probability(c1, c2);
    			
    			if(cumul > p) {
    				return new Task(id, c1, c2, 0, distribution.weight(c1, c2));
    			}
    		}
    	}
    	
    	return null;
    }
    
    private double getMeanFutureCost(TaskSet tasks, int horizon, int samples) {
    	double cost = 0;
    	
//    	if(tasks.size() > 20) {
//    		return this.cost;
//    	}
    	
    	horizon = Math.min(horizon, Math.max(20 - tasks.size(), 0));
    	
    	for(int i = 0; i < samples; i++) {
    		Task[] universe = new Task[tasks.size() + horizon];
    		
    		int j = 0;
    		for(Task t : tasks) {
    			universe[j] = new Task(j, t.pickupCity, t.deliveryCity, t.reward, t.weight);
    			j++;
    		}
    		for(int k = 0; k < horizon; k++) {
    			universe[j] = this.getRandomTask(j);
    			j++;
    		}
    		TaskSet taskSet = TaskSet.create(universe);
    		
    		Solution sol = this.computePlan(agent.vehicles(), taskSet);
    		cost += sol.getCost();
    	}
    	
    	return cost/samples;
    }
    
    private void computeMeanCostPerTask() {
    	int horizon = 20;
    	int sampleSize = 10;
    	
    	for(int i = 0; i < horizon; i++) {
    		this.meanCostPerTask.add(0.);
    	}
    	
    	for(int i = 0; i < sampleSize; i++) {
    		Task[] tasks = new Task[horizon];
    		for(int j = 0; j < horizon; j++) {
    			tasks[j] = this.getRandomTask(j);
    		}
    		TaskSet taskSet = TaskSet.noneOf(TaskSet.create(tasks));
    		
    		for(int j = 0; j < horizon; j++) {
    			taskSet.add(tasks[j]);
    			
    			Solution sol = this.computePlan(agent.vehicles(), taskSet);
    			this.meanCostPerTask.set(j, sol.getCost());
    		}
    	}
    	
    	for(int i = 0; i < horizon; i++) {
    		double costPerTask = this.meanCostPerTask.get(i)/(i + 1)/sampleSize;
    		this.meanCostPerTask.set(i, costPerTask);
    		System.out.println("Mean cost for " + (i + 1) + " tasks: " + costPerTask);
    	}
    }
    
    private double getMeanCostPerTask(int numberOfTasks) {
    	if(numberOfTasks < 1)
    		return 0;
    	else {
    		int id = Math.min(numberOfTasks, meanCostPerTask.size());
    		return meanCostPerTask.get(id - 1);
    	}
    }
    
    private Solution computePlan(List<Vehicle> vehicles, TaskSet tasks) {
    	Solution initSol = this.getInitialSolution(vehicles, tasks);
    	
    	if(initSol == null)
    		return null;
    	
    	int stagn;
    	int numberOfTasks = tasks.size();
    	if(numberOfTasks < 5)
    		stagn = 10*numberOfTasks;
    	else if(numberOfTasks < 10)
    		stagn = 50*(numberOfTasks - 4);
    	else
    		stagn = 100*(numberOfTasks - 7);
    	
    	return this.computeSLS(initSol, 0.5, 20000, stagn, 100, 2, false);
    }
    
    private Solution computePlan(Solution lastSolution, Task newTask) {
    	Solution initSol = new Solution(lastSolution);
    	initSol.addTask(newTask);
    	
    	int stagn;
    	int numberOfTasks = initSol.getNumberOfTasks();
    	if(numberOfTasks < 5)
    		stagn = 10*numberOfTasks;
    	else if(numberOfTasks < 10)
    		stagn = 50*(numberOfTasks - 4);
    	else
    		stagn = 100*(numberOfTasks - 7);
    	
    	return this.computeSLS(initSol, 0.5, 20000, stagn, 100, 2, false);
    }
	
    // Return a solution with the tasks randomly spread between the vehicles. Return
    // null if not possible.
    private Solution getInitialSolution(List<Vehicle> vehicles, TaskSet tasks) {
    	Solution initSol = new Solution();
    	
    	Random random = new Random();
    	Map<Vehicle, List<Task>> tasksPerVehicle = new HashMap<Vehicle, List<Task>>();
    	
    	// Create an empty list of Task for each vehicle
    	for(Vehicle v : vehicles) {
    		tasksPerVehicle.put(v, new ArrayList<Task>());
    	}
    	
    	// For each task
    	for(Task t : tasks) {
    		List<Vehicle> admissibleVehicles = new ArrayList<Vehicle>();
    		
    		// Compute the list of admissible vehicle for this task, i.e. the ones with
    		// a capacity big enough
    		for(Vehicle v : vehicles) {
    			if(v.capacity() >= t.weight)
    				admissibleVehicles.add(v);
    		}
    		
    		// If the list is empty, the problem in unsolvable
    		if(admissibleVehicles.isEmpty())
    			return null;
    		
    		// Add the task to a random admissible vehicle
    		int i = random.nextInt(admissibleVehicles.size());
    		tasksPerVehicle.get(admissibleVehicles.get(i)).add(t);
    	}
    	
    	// Create the solution with the computed task distribution
    	for(Map.Entry<Vehicle, List<Task>> entry : tasksPerVehicle.entrySet()) {
    		initSol.putVehicle(entry.getKey(), entry.getValue());
    	}
    	
    	return initSol;
    }
    
    // Compute the stochastic local search algorithm with the given initial solution and
    // parameters.
    // randomFactor: probability to keep the last solution if the new one is worse
    // maxIter: overall maximum iterations allowed
    // maxStagnationIter: number of iterations allowed without finding a new best solution
    // maxLocalStagnationIter: number of iterations with no improvement of the local best
    //		solution before applying a perturbation
    // perturbationSteps: number of random steps performed for the perturbation
    // showPlot: whether to show a live plot of the results or not
    private Solution computeSLS(Solution initSolution, double randomFactor,
    		int maxIter, int maxStagnationIter, int maxLocalStagnationIter,
    		int pertubationSteps, boolean showPlot) {
    	Solution A = initSolution, best = initSolution;
    	double cost = initSolution.getCost();
    	double overallBestCost = Double.POSITIVE_INFINITY, localBestCost = Double.POSITIVE_INFINITY;
    	Random random = new Random();
    	int iter = 0, stagnationIter = 0, localStagnationIter = 0;

    	// Setup graph
    	JFrame frame;
    	Plot plot = null;
    	if(showPlot) {
    		frame = new JFrame("SLS Algorithm, Cost over iterations");
    		plot = new Plot();
    		plot.setTitle("SLS Algorithm, Cost over iterations");
    		plot.setXLabel("Iteration n°");
    		plot.setYLabel("Cost");
    		plot.addLegend(0, "Current");
    		plot.addLegend(1, "Best");
    		plot.setYLog(true);
        	frame.add(plot);
        	frame.pack();
        	frame.setVisible(true);
    	}
    	
    	// Search until we reached maxIter or didn't find a better solution for a while
    	while(iter < maxIter && stagnationIter < maxStagnationIter) {
    		List<Solution> neighbours;
    		
    		// If we are trapped in a local minima
    		if(localStagnationIter >= maxLocalStagnationIter) {
    			// Perform some random steps
    			for(int i = 0; i < pertubationSteps; i++) {
    				neighbours = A.getNeighbours();
	    			int randomID = random.nextInt(neighbours.size());
	    			A = neighbours.get(randomID);
    			}
    			
    			localStagnationIter = 0;
    			cost = A.getCost();
    			localBestCost = cost;
    		}
    		else {
    			double bestCost = Double.POSITIVE_INFINITY;
    			List<Solution> bestSolutions = new ArrayList<Solution>();
        		double oldCost = cost;
    			
        		// Get the neighbours of the current solution
    			neighbours = A.getNeighbours();
    			
    			// Find the neighbour solutions with the lowest cost
    			for(Solution s : neighbours) {
    				cost = s.getCost();
    				
    				if(cost == bestCost) {
    					bestSolutions.add(s);
    				}
    				if(cost < bestCost) {
    					bestSolutions.clear();
    					bestSolutions.add(s);
    					bestCost = cost;
    				}
    			}
    			
    			// If this cost is still higher than the current cost, keep the current
    			// solution with a certain probability
        		if(bestCost > oldCost && random.nextDouble() < randomFactor) {
					cost = oldCost;
				}
				else {
					// Otherwise choose one of the best neighbours at random
					int id = random.nextInt(bestSolutions.size());
	    			A = bestSolutions.get(id);
					cost = bestCost;
				}
    		}
    		
    		// If the new cost is better than the local best one, update the local best
    		// one and reset the local stagnation counter
    		if(cost < localBestCost) {
    			localBestCost = cost;
    			localStagnationIter = 0;
    		}
    		
    		// If the new cost is better than the overall best one, update the overall
    		// best one and reset the stagnation counter
			if(cost < overallBestCost) {
    			best = A;
    			overallBestCost = cost;
    			stagnationIter = 0;
    		}
    		
			// Update the plot
    		if(showPlot) {
	        	plot.addPoint(0, iter, cost, true);
	        	plot.addPoint(1, iter, overallBestCost, true);
	        	plot.fillPlot();
    		}
    		
    		iter++;
    		stagnationIter++;
    		localStagnationIter++;
    	}
    	
//    	if(iter == maxIter) {
//    		System.out.println("Stopped because max iter reached.");
//    	}
//    	else {
//    		System.out.println("Stopped because stagnated for too long. iter = " + iter);
//    	}
//    	
//    	System.out.println("Final cost: " + best.getCost());

    	return best;
    }
}