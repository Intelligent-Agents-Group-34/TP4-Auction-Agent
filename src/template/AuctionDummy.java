package template;

//the list of imports
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
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.task.TaskSet;
import logist.topology.Topology;
import ptolemy.plot.Plot;


public class AuctionDummy implements AuctionBehavior {

	private Agent agent;
	private double factor;
	private double cost;

	@Override
	public void setup(Topology topology, TaskDistribution distribution,
			Agent agent) {
		this.agent = agent;
		this.factor = agent.readProperty("factor", Double.class, 1.);
		this.cost = 0;
	}
	
	@Override
	public Long askPrice(Task task) {
		
		TaskSet tasks = agent.getTasks().clone();
		tasks.add(task);
		
		Solution sol = this.computePlan(agent.vehicles(), tasks);
		
		if(sol == null)
			return null;

		double marginalCost = sol.getCost() - cost;
		
		return (long)(marginalCost*factor);
	}
	
	@Override
	public void auctionResult(Task task, int winner, Long[] offers) {
		if(winner != agent.id())
			return;
					
		Solution sol = this.computePlan(agent.vehicles(), agent.getTasks());
		
		this.cost = sol.getCost();
	}
	
    @Override
    public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks) {
        long time_start = System.currentTimeMillis();
        
        List<Plan> plans = new ArrayList<Plan>();
        
        // Compute a good plan with the SLS algorithm
        Solution sol = this.computePlan(vehicles, tasks);
        
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
    
    private Solution computePlan(List<Vehicle> vehicles, TaskSet tasks) {
    	Solution initSol = this.getInitialSolution(vehicles, tasks);
    	
    	if(initSol == null)
    		return null;
    	
    	return this.computeSLS(initSol, 0.5, 20000, 2000, 100, 2, false);
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
    	
    	if(iter == maxIter) {
    		System.out.println("Stopped because max iter reached.");
    	}
    	else {
    		System.out.println("Stopped because stagnated for too long. iter = " + iter);
    	}
    	
    	System.out.println("Final cost: " + best.getCost());

    	return best;
    }
}
