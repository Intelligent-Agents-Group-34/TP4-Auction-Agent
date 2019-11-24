package template;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.swing.JFrame;

import logist.agent.Agent;
import logist.behavior.AuctionBehavior;
import logist.plan.Plan;
import logist.simulation.Vehicle;
import logist.simulation.VehicleImpl;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.task.TaskSet;
import logist.topology.Topology;
import logist.topology.Topology.City;
import ptolemy.plot.Plot;

public class AuctionAgent implements AuctionBehavior {
	private final boolean verbose = true;
	private Agent agent;
	private final int n0 = 3; // "Early game"
	private final long bid0 = 1000; // First bid
	private long lastBid;
	private final int minOpponentBidHorizon = 5;
	private double cost;
	private double newCost;
	private Solution solution;
	private Solution newSolution;
	private Task lastTask;
	
	private List<Double> opponentBids;
	private List<Task> opponentTasks;
	private List<Double> opponentCms;
	private double opponentCost;
	private Solution opponentSolution;
	private final int opponentCmMinSamples = 3;

	@Override
	public void setup(Topology topology, TaskDistribution distribution,
			Agent agent) {
		this.agent = agent;
		this.lastBid = 0;
		this.cost = 0;
		this.newCost = 0;
		this.solution = this.getInitialSolution(agent.vehicles(), TaskSet.create(new Task[0]));
		this.newSolution = new Solution(solution);
		
		this.opponentBids = new ArrayList<Double>();
		this.opponentTasks = new ArrayList<Task>();
		this.opponentCms = new ArrayList<Double>();
		this.opponentCost = 0;
		this.opponentSolution = new Solution(solution);
	}
	
	@Override
	public Long askPrice(Task task) {
		int n = agent.getTotalTasks(); // Number of tasks we currently have
		int nOpponent = opponentTasks.size(); // Number of task the opponent has
		if(verbose)
			System.out.println("Tasks: " + n + " / " + nOpponent);
		
		long startTime = System.currentTimeMillis();
		// Save it so we can replace it later with the correct one (due to logist constrains)
		this.lastTask = task;
		
		//// Compute marginal cost
		// Compute the solution with the new task and its cost
		this.newSolution = this.computePlan(solution, task);
		if(newSolution == null) // Don't take the task if we can't handle it
			return null;
		this.newCost = newSolution.getCost();
		
		// If the new cost is less than the old one, it means that the old solution is
		// bad. Thus we replace it with the new solution but without the new task.
		if(newCost < cost) {
			this.solution = new Solution(newSolution);
			this.solution.removeTask(task);
			this.cost = solution.getCost();
		}
		
		// Marginal cost
		double Cm = newCost - cost;
		if(verbose) {
			System.out.println("Time to compute Cm: "
					+ (System.currentTimeMillis() - startTime) + " ms");
			
			System.out.println("Marginal Cost: " + Cm);
		}
		
		// Compute the minimum bid from the opponent over the last X auctions
		double minOpponentBid = Double.POSITIVE_INFINITY;
		int minOpponentBidCount = Math.min(opponentBids.size(), minOpponentBidHorizon);
		if(minOpponentBidCount == 0)
			minOpponentBid = 0;
		else {
			for(int i = 0; i < minOpponentBidCount; i++) {
				minOpponentBid = Math.min(minOpponentBid, opponentBids.get(opponentBids.size() - 1 - i));
			}
		}
		
		// If we have only a few tasks, apply a specific strategy
		if(n < n0) {
			if(nOpponent == 0) {
				return bid0;
			}
			else if(nOpponent == 1) {
				return (lastBid/2);
			}
			else {
				return (long)(0.8*minOpponentBid);
			}
		}
		
		double bid = Cm;
		
		//// If we have a sample size of opponent marginal costs big enough, compute a
		//// linear regression model to predict the next opponent bid.
		int opponentCmCount = opponentCms.size();
		if(opponentCmCount > opponentCmMinSamples) {
			// X = opponent marginal costs
			// Y = opponent bids
			
			// Compute the means
			double mx = 0, my = 0;
			for(int i = 0; i < opponentCmCount; i++) {
				mx += opponentCms.get(opponentCmCount - 1 - i);
				my += opponentBids.get(opponentBids.size() - 1 - i);
			}
			mx /= opponentCmCount;
			my /= opponentCmCount;
			
			// Compute the standard deviations and the covariance
			double sx = 0, sy = 0, sxy = 0;
			for(int i = 0; i < opponentCmCount; i++) {
				double x = opponentCms.get(opponentCmCount - 1 - i);
				double y = opponentBids.get(opponentBids.size() - 1 - i);
				sx += (x - mx)*(x - mx);
				sy += (y - my)*(y - my);
				sxy += (x - mx)*(y - my);
			}
			sx /= opponentCmCount - 1;
			sx = Math.sqrt(sx);
			sy /= opponentCmCount - 1;
			sy = Math.sqrt(sy);
			sxy /= opponentCmCount - 1;
			
			double predictedOpponentBid;
			double sLinRegError;
			
			if(sx != 0) {
				// Compute the slope and the intercept of the regression line
				double m = sxy/(sx*sx);
				double b = my - m*mx;
				
				// Compute the standard deviation of the error between the regression line
				// and the actual values so we have an appreciation of the fitness
				sLinRegError = 0;
				for(int i = 0; i < opponentCmCount; i++) {
					double x = opponentCms.get(opponentCmCount - 1 - i);
					double y = opponentBids.get(opponentBids.size() - 1 - i);
					sLinRegError += (y - (m*x + b))*(y - (m*x + b));
				}
				sLinRegError /= opponentCmCount - 1;
				sLinRegError = Math.sqrt(sLinRegError);
				
				// Compute the opponent new cost
				Solution opponentNewSolution = this.computeOpponentPlan(task);
				double opponentNewCost = opponentNewSolution.getCost();
				if(opponentNewCost < opponentCost) {
					this.opponentSolution = new Solution(opponentNewSolution);
					this.opponentSolution.removeTask(task);
					this.opponentCost = this.opponentSolution.getCost();
				}
				
				// Predict the opponent bid with the model
				predictedOpponentBid = m*(opponentNewCost - opponentCost) + b;
			}
			else { // Can happen if all x values are the same.
				// In this case, use the mean of y-values as the prediction
				predictedOpponentBid = my;
				// And the standard deviation of the y-values as the standard deviation of the
				// error
				sLinRegError = sy;
			}
			
			if(verbose) {
				System.out.println("Predicted ennemy bid: " + predictedOpponentBid);
				System.out.println("Standard deviation: " + (3*sLinRegError));
			}
			
			// If the predicted opponent bid is higher than our bid, we can (theoretically)
			// raise our bid and still get the task. If the predicted bid is lower than our
			// bid, we can lower our bid to the mean of our bid and the opponent one (see report
			// for details)
			if(bid < predictedOpponentBid - 3*sLinRegError - 10)
				bid = predictedOpponentBid - 3*sLinRegError - 10;
			else if(bid > predictedOpponentBid + 3*sLinRegError)
				bid = (bid + predictedOpponentBid + 3*sLinRegError)/2;
		}
		
		// Safety measure
		bid = Math.max(bid, 0.8*minOpponentBid);
		
		if(verbose)
			System.out.println("Bid: " + bid + "\n");
		
		return (long)bid;
	}
	
	@Override
	public void auctionResult(Task task, int winner, Long[] offers) {
		this.lastBid = offers[agent.id()];
		
		// Compute the new opponent solution and its cost
		Solution opponentNewSolution = this.computeOpponentPlan(task);
		double opponentNewCost = opponentNewSolution.getCost();
		if(opponentNewCost < opponentCost) {
			this.opponentSolution = new Solution(opponentNewSolution);
			this.opponentSolution.removeTask(task);
			this.opponentCost = this.opponentSolution.getCost();
		}
		
		int opponentAgentID = 1 - agent.id();
		if(offers[opponentAgentID] != null) {
			// Save opponent bid
			double opponentBid = (double)offers[opponentAgentID];
			this.opponentBids.add(opponentBid);
			
			// Save opponent marginal cost. Discard it if the opponent is still in early game
			if(opponentTasks.size() > 0)
				this.opponentCms.add(opponentNewCost - opponentCost);
		}
		
		if(winner == agent.id()) {
			// If we won the task, update our plan
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
			// If the opponent won the task, update their tasklist and their plan
			Task t = new Task(opponentTasks.size(), task.pickupCity, task.deliveryCity, task.reward, task.weight);
			this.opponentTasks.add(t);
			this.opponentSolution = opponentNewSolution;
			this.opponentCost = opponentNewCost;
		}
	}
	
    @Override
    public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks) {
        long time_start = System.currentTimeMillis();
        
        List<Plan> plans = new ArrayList<Plan>();
        
        Solution sol = solution;
        
        if(sol == null) {
            for(Vehicle v : vehicles) {
            	plans.add(Plan.EMPTY);
            }
        }
        else {
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
    
    private Solution computeOpponentPlan(Task task) {
    	City initCity;
    	if(opponentTasks.size() == 0)
    		initCity = task.pickupCity;
    	else
    		initCity = opponentTasks.get(0).pickupCity;
    	Vehicle v = new VehicleImpl(0, "Ennemy", Integer.MAX_VALUE, 5, initCity, 5, Color.BLACK).getInfo();
    	List<Vehicle> vehicles = new ArrayList<Vehicle>();
    	vehicles.add(v);
    	Task[] ennemyTasksArray = new Task[opponentTasks.size() + 1];
    	int i = 0;
    	for(Task t : opponentTasks) {
    		ennemyTasksArray[i++] = t;
    	}
    	Task t = new Task(i, task.pickupCity, task.deliveryCity, task.reward, task.weight);
    	ennemyTasksArray[i] = t;
    	TaskSet tasks = TaskSet.create(ennemyTasksArray);
    	
    	return this.computePlan(vehicles, tasks);
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