package template;

import java.io.File;
//the list of imports
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import logist.LogistSettings;
import logist.Measures;
import logist.agent.Agent;
import logist.behavior.AuctionBehavior;
import logist.config.Parsers;
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
public class AuctionPoupic implements AuctionBehavior {

	enum Strategy {NAIVE, A, B, C }
	
	Strategy strategy;
	
	/*
	 * centralized
	 */
	private static final int NBMAXITER = 300000;
	private static final int RANGELOCALMINIMA = 10000;
	private static final int TIMEOUTMARGE = 10000;
	private static final double PROB2SEESWAP = 0.5;
	private static final double PROB2DIGUP = 0.3;
	private static final int MAXITERWITHOUTIMPROV = 100000;
	private static final int MINITERTOBIGSWAP = 800;
	private static final int DELAYTOFINDSWAP = 50;

	private long timeout_setup;
	private long timeout_plan;

	/*
	 * auction
	 */
	
	private Topology topology;
	private TaskDistribution distribution;
	private Agent agent;
	private Random random;
	private Vehicle vehicle;
	private List<Vehicle> vehicles;
	private City currentCity;
	private ArrayList<Long> myBidsHistory = new ArrayList<Long>();
	private ArrayList<Long> bidsOppenentHistory = new ArrayList<Long>();
	private ArrayList<TaskSet> tasksOfAllAgentsList = new ArrayList<TaskSet>();
	private Long lastBid;
	private Long lastOpponentBid;
	private TaskSet tasksA;
	private TaskSet tasksB;
	private TaskSet tasksC;
	private int maxNumberOfCompanies = 10;
	private int[] sumOfReward = new int[maxNumberOfCompanies];
	private int[] numberOfTaskTaken = new int[maxNumberOfCompanies];
	private TaskSet[] tasksOfAllAgents = new TaskSet[maxNumberOfCompanies];
	private int sumOfRewardB = 0;
	private int sumOfRewardC = 0;
	private int[] actualPrice = new int[maxNumberOfCompanies];
	private AtomicInteger actualPriceA = new AtomicInteger(); 
	private AtomicInteger actualPriceB = new AtomicInteger(); 
	private AtomicInteger actualPriceC = new AtomicInteger(); 
	private int[] newPrice = new int[maxNumberOfCompanies];
	private AtomicInteger newPriceA = new AtomicInteger(); 
	private AtomicInteger newPriceB = new AtomicInteger(); 
	private AtomicInteger newPriceC = new AtomicInteger();
	private double margeOpponentRatio = 1;
	private double expectedCostPerKm;
	private double expectedCostPerTask;
	private double expectedPathLengthForOneTask;

	@Override
	public void setup(Topology topology, TaskDistribution distribution,
			Agent agent) {

		// this code is used to get the timeouts
		LogistSettings ls = null;
		try {
			ls = Parsers.parseSettings("config" + File.separator + "settings_auction.xml");
		}
		catch (Exception exc) {
			System.out.println("There was a problem loading the configuration file.");
		}

		// the setup method cannot last more than timeout_setup milliseconds
		timeout_setup = ls.get(LogistSettings.TimeoutKey.SETUP);
		// the plan method cannot execute more than timeout_plan milliseconds
		timeout_plan = ls.get(LogistSettings.TimeoutKey.PLAN);


		this.topology = topology;
		this.distribution = distribution;
		this.agent = agent;
		this.vehicle = agent.vehicles().get(0);
		this.currentCity = vehicle.homeCity();
		this.vehicles = agent.vehicles();

		long seed = -9019554669489983951L * currentCity.hashCode() * agent.id();
		this.random = new Random(seed);

		String strategymName = agent.readProperty("algorithm", String.class, "NAIVE");
		

		Task[] nullTask = new Task[0];
		TaskSet nullTaskSet = TaskSet.create(nullTask);

		for (int i = 0; i < maxNumberOfCompanies; i++) {
			tasksOfAllAgentsList.add(nullTaskSet);
			tasksOfAllAgents[i] = nullTaskSet;
		}
		
		
		
		// Throws IllegalArgumentException if algorithm is unknown
		strategy = Strategy.valueOf(strategymName.toUpperCase());
		switch (strategy) {

		case NAIVE:
			break;

		case A:
			setupStrategyA( topology, distribution, agent);
			break;
		case B:
			setupStrategyB( topology, distribution, agent);
			break;
		case C:
			setupStrategyC( topology, distribution, agent);
			break;
		default:
			throw new AssertionError("Should not happen.");
		}	
			
		

	}
	
	void setupStrategyA(Topology topology, TaskDistribution distribution, Agent agent) {
		
	}
	
	void setupStrategyB(Topology topology, TaskDistribution distribution, Agent agent) {
		
	}
	
	void setupStrategyC(Topology topology, TaskDistribution distribution, Agent agent) {
		
		print("\n");
		
		AtomicInteger price = new AtomicInteger(); 
		
		double sumExpectedCostPerKm = 0;
		double sumExpectedCostPerTask = 0;
		double sumExpectedPathLength = 0;
		
		int expectedWeight = 3;
		int expectedNumbberTotalOfTask = 20;
		int exectedNumberOfCompanies = 2;
		int expectedNumbberOfTaskGet = expectedNumbberTotalOfTask/exectedNumberOfCompanies;
		
		int reward = 1;
		int repeat = 20;
		int sumPath;
		int numberOfCity = topology.cities().size();
		int randomPickupCity;
		int randomDeliverCity;
		City pickupCity;
		City deliverCity;
		Task[] universe = new Task[expectedNumbberOfTaskGet];
		Task task;
		
		
		/*
		 * print probability distribution
		 * 
		
		for (int i = 0; i < topology.cities().size(); i++) {
			for (int j = 0; j < topology.cities().size(); j++) {
			
				pickupCity = topology.cities().get(i);
				deliverCity = topology.cities().get(j);
				
				print(" pickup City, deliver City: " + pickupCity + " , " + deliverCity + " % proba: " + 100 * distribution.probability(pickupCity, deliverCity) + "%");
				
			}
		}
		*/
		
		/*
		 * exepected cost for a given number of task for expected weight = 3
		 * 
		 */
		/*
		Fianl expectedCostPerKm: 12.063288341958623 for number of task: 1
		Fianl expectedCostPerKm: 6.738028540998125 for number of task: 2
		Fianl expectedCostPerKm: 5.582343734189006 for number of task: 3
		Fianl expectedCostPerKm: 5.100042181511188 for number of task: 4
		Fianl expectedCostPerKm: 4.886561423707628 for number of task: 5
		Fianl expectedCostPerKm: 4.411852221161017 for number of task: 6
		Fianl expectedCostPerKm: 3.789763912279059 for number of task: 7
		Fianl expectedCostPerKm: 3.613135436665889 for number of task: 8
		Fianl expectedCostPerKm: 3.419476660953969 for number of task: 9
		Fianl expectedCostPerKm: 3.0834873447145204 for number of task: 10
		Fianl expectedCostPerKm: 2.96922760743084 for number of task: 11
		Fianl expectedCostPerKm: 2.768927215756731 for number of task: 12
		Fianl expectedCostPerKm: 2.666160899935452 for number of task: 13
		Fianl expectedCostPerKm: 2.5222568001321957 for number of task: 14
		Fianl expectedCostPerKm: 2.3901580271838156 for number of task: 15
		Fianl expectedCostPerKm: 2.2350121412429425 for number of task: 16
		
		with 25 repeat
		Fianl expectedCostPerKm: 2.1905015292227077 for number of task: 17
		Fianl expectedCostPerKm: 2.10565677914022 for number of task: 18
		Fianl expectedCostPerKm: 2.0062435151840283 for number of task: 19
		Fianl expectedCostPerKm: 1.9763357395050085 for number of task: 20
		
		
		
		 */
		/*
		for (int k = 19; k < 21; k++) {
		
		universe = new Task[k];
		sumExpectedCostPerKm = 0;
		sumExpectedCostPerTask = 0;
		sumExpectedPathLength = 0;
		
		for (int i = 0; i < repeat; i++) {
			
			sumPath = 0;

			for (int j = 0; j < universe.length; j++) {


				randomPickupCity = (int) (Math.random() * numberOfCity);
				randomDeliverCity = (int) (Math.random() * numberOfCity);
				while(randomDeliverCity == randomPickupCity) {
					randomDeliverCity = (int) (Math.random() * numberOfCity);
				}
				pickupCity = topology.cities().get(randomPickupCity);
				deliverCity = topology.cities().get(randomDeliverCity);
				task = new Task(j, pickupCity, deliverCity, reward, expectedWeight);
				universe[j] = task;
				sumPath += Measures.unitsToKM(pickupCity.distanceUnitsTo(deliverCity));
			}
			

			TaskSet taskSet = TaskSet.create(universe);
			
			centralizedplan(vehicles, taskSet, 0.019, price);

			//print("expectedCostPerKm: " + price.doubleValue()/sumPath + " progression " + i);
			
			sumExpectedCostPerKm += price.doubleValue()/sumPath;
			sumExpectedCostPerTask += price.doubleValue()/k; 
			sumExpectedPathLength += sumPath;

		}
		
		expectedCostPerKm = sumExpectedCostPerKm/repeat;
		expectedCostPerTask = sumExpectedCostPerTask/repeat;
		expectedPathLengthForOneTask = sumExpectedPathLength/repeat/universe.length;
		
		//expectedCostPerKm = 3.182397020909956 ; // compute with 500 repetition for 10 tasks;
		
		print("\nfor number of task: " + k 
				+ "\nFinal expectedCostPerKm: " + expectedCostPerKm
				+ "\nFinal expectedCostPerTask: " + expectedCostPerTask
				+ "\nFinal expectedPathLengthForOneTask: " + expectedPathLengthForOneTask);
		
		}
		*/
		
//		expectedCostPerKm = 2.2;
//		expectedCostPerTask = 540;
//		expectedPathLengthForOneTask = 250;
		
		expectedCostPerKm = 2;
		expectedCostPerTask = 450;
		expectedPathLengthForOneTask = 250;
		
		print("\n");
	}

	@Override
	public void auctionResult(Task previous, int winner, Long[] bids) {

		switch (strategy) {
		case NAIVE:
			if (winner == agent.id()) {
				currentCity = previous.deliveryCity;
			}
			break;

		case A:
			if (winner == agent.id()) {
				actualPriceA.set(newPriceA.intValue());
			}
			else {
				tasksA.remove(previous);
			}
			break;
		case B:
			//print("B id: "+agent.id());
			if (winner == agent.id()) {
				sumOfRewardB += bids[winner];
				actualPriceB.set(newPriceB.intValue());
			}
			else {
				tasksB.remove(previous);
			}
			print("benef for B: " + (sumOfRewardB - actualPriceB.intValue()) + " wtih " + agent.getTotalTasks() + " tasks taken, after bid N° " + previous.id);
			break;
		case C:
			//print("C id: "+agent.id());
			numberOfTaskTaken[winner] += 1;
			sumOfReward[winner] += bids[winner];
			
			for (int i = 0; i < bids.length; i++) {
				if (i == agent.id()) {
					lastBid = bids[agent.id()];
					myBidsHistory.add(lastBid);
				}
				else {
					lastOpponentBid = bids[i];
					bidsOppenentHistory.add(lastOpponentBid);
				}
			}
			
			//print(bids[agent.id()]);
			//print(bids[1-agent.id()]);
			
			
			//margeOpponentRatio = (0.5 + 0.5 * margeOpponentRatio) * (1 +  0.5 * (bids[1-agent.id()] - bids[agent.id()])/bids[agent.id()]);
			
			//print(margeOpponentRatio);
			
			//margeOpponentRatio = Math.max(1, (0.5 + 0.5 * margeOpponentRatio) * (1 +  0.5 * (bids[1-agent.id()] - bids[agent.id()])/bids[agent.id()]));
			margeOpponentRatio = Math.max(1, 0.5 + (0.5 * margeOpponentRatio) * (1 +  0.5 * (bids[1-agent.id()] - bids[agent.id()])/bids[agent.id()]));
			
			if (winner == agent.id()) {
				sumOfRewardC += bids[winner];
				actualPriceC.set(newPriceC.intValue());
			}
			else {
				tasksC.remove(previous);
				}
			
			print("\nbid N°: "+ previous.id + ", agent bidded: " + lastBid + " and oppenent bidded: " + lastOpponentBid);
			print("benef for C: " + (sumOfRewardC - actualPriceC.intValue()) + " wtih " + agent.getTotalTasks() + " tasks taken, after bid N° " + previous.id);
			print("margeOpponenRatio: " + margeOpponentRatio);
			
			
			break;
		default:
			throw new AssertionError("Should not happen.");
		}		
	}
	
	
	
	public long priceStrategyA(Task task) {
		
		
		if(tasksA == null)
			tasksA = agent.getTasks();
		tasksA.add(task);
		
		//CentralizedTemplate centralizedTemplate = new CentralizedTemplate();
		//centralizedTemplate.centralizedplan(vehicles, TasksA, 0.01, actualPrice);
		//centralizedTemplate.centralizedplan(vehicles, TasksA, 0.01, newPrice);
		
		centralizedplan(vehicles, tasksA, 0.01, newPriceA);
		
		
		long marginalCost = newPriceA.longValue() - actualPriceA.longValue();
		long pathCost = (long) (Measures.unitsToKM(task.pickupCity.distanceUnitsTo(task.deliveryCity)) * Math.min(agent.vehicles().get(0).costPerKm(), agent.vehicles().get(1).costPerKm()));
		long bid = (marginalCost + pathCost)/2;
		
		print("nb taks: " + (tasksA.size()-1) + " actual price: " + actualPriceA + " new price: " + newPriceA  +" bid: "+ bid);
		
		return bid;
	}

	public Long priceStrategyB(Task task) {

		int benef = sumOfRewardB - actualPriceB.intValue();

		if(tasksB == null)
			tasksB = agent.getTasks();
		tasksB.add(task);

		centralizedplan(vehicles, tasksB, 0.01, newPriceB);

		long price = (long) (550 + (Math.random()-0.5) *0);
		
		//print("bid for B: " + price);
		
		return price;
	}

	public Long priceStrategyC(Task task) {

		int benef = sumOfRewardC - actualPriceC.intValue();
		
		if(tasksC == null)
			tasksC = agent.getTasks();
		tasksC.add(task);
		
		centralizedplan(vehicles, tasksC, 0.01, newPriceC);
		
		long pathLength = (long) Measures.unitsToKM(task.pickupCity.distanceUnitsTo(task.deliveryCity));
		long pathCost = (long) (pathLength * expectedCostPerKm);
		long marginalCost = newPriceC.longValue() - actualPriceC.longValue();
		
		double marginalCostratio = Math.min(0.0 +  (double)(numberOfTaskTaken[agent.id()])/20 , 0.5);
		
		
		long bid1 = (long) (expectedCostPerTask * pathLength/expectedPathLengthForOneTask);
		long bid2 = (long) (0.25 * pathCost +  0.75 * expectedCostPerTask);
		long bid3 = (long) expectedCostPerTask;
		
		
		
		long bid = (long) (bid2);
		
		
		if (marginalCost > bid) // marginalCost != 0
			bid = (long) (marginalCost * marginalCostratio + bid * (1-marginalCostratio));
		
		bid = (long) (bid * margeOpponentRatio);
		
		//print("bid for C: " + bid);
		
		return bid;
	}
	
//	@Override
	public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks) {
		
//		System.out.println("Agent " + agent.id() + " has tasks " + tasks);
/*
		Plan planVehicle1 = naivePlan(vehicle, tasks);

		List<Plan> plans = new ArrayList<Plan>();
		plans.add(planVehicle1);
		while (plans.size() < vehicles.size())
			plans.add(Plan.EMPTY);

		return plans;
		*/
		
		AtomicInteger finalCost = new AtomicInteger();
		double timeAllowed = 0.1;
		
		print("agent: " + agent.name() + " number of tasks: " + tasks.size());
		
		//CentralizedTemplate centralizedTemplate = new CentralizedTemplate();
		
		//return centralizedTemplate.centralizedplan(vehicles, tasks, timeAllowed, finalCost);
		
		return centralizedplan(vehicles, tasks, 1, finalCost);
	}

	@Override
	public Long askPrice(Task task) {
		
		//print("\nbide N°:" + task.id);
		
		if (task.weight > vehicle.capacity())
			return null;
		
		long price;
		
		switch (strategy) {

		case NAIVE:
			// ...
			price = naivePrice(task);
			break;

		case A:
			// ...
			price = priceStrategyA(task);
			break;
		case B:
			// ...
			price = priceStrategyB(task);
			//plan = naivePlan(vehicle, tasks);
			break;
		case C:
			// ...
			//print("\nbide N°:" + task.id);
			price = priceStrategyC(task);
			//plan = naivePlan(vehicle, tasks);
			break;
		default:
			throw new AssertionError("Should not happen.");
		}		
		
		return price;
	}
	
	public long naivePrice(Task task) {

		long distanceTask = task.pickupCity.distanceUnitsTo(task.deliveryCity);
		long distanceSum = distanceTask
				+ currentCity.distanceUnitsTo(task.pickupCity);
		double marginalCost = Measures.unitsToKM(distanceSum
				* vehicle.costPerKm());
		
		double ratio = 1.0 + (random.nextDouble() * 0.05 * task.id);
		double bid = ratio * marginalCost;
		
		return (long) Math.round(bid);
	}
	
	private Plan naivePlan(Vehicle vehicle, TaskSet tasks) {
		City current = vehicle.getCurrentCity();
		Plan plan = new Plan(current);

		for (Task task : tasks) {
			// move: current city => pickup location
			for (City city : current.pathTo(task.pickupCity))
				plan.appendMove(city);

			plan.appendPickup(task);

			// move: pickup location => delivery location
			for (City city : task.path())
				plan.appendMove(city);

			plan.appendDelivery(task);

			// set current city
			current = task.deliveryCity;
		}
		return plan;
	}
	
	/*
	 * Centralized
	 */
	
	public class Action {
		Task task = null;
		boolean deliver = false;
		int id;
		City destination;

		public Action(Task t, boolean d) {
			task = t;
			deliver = d; // 0 for pickup, 1 for delivering
			if(task != null) {
				id = 2 * task.id + (deliver? 1 : 0);
			}
			destination = deliver? task.deliveryCity : task.pickupCity;
		}
	}

	public Action id2Action(int id, ArrayList<Task> taskList) {

		int task_id = (int) id/2;

		return new Action(taskList.get(task_id), id%2 == 0? false: true);
	}

	public boolean isValid(ArrayList<Integer> solutionList, ArrayList<Task> tasks, int capacity) {
		if(solutionList == null)
			return false;

		int sumWeight = 0;
		Action nextAction;

		for(int i = 0; i < solutionList.size()-1; i++) {

			nextAction = id2Action(solutionList.get(i), tasks);
			if (nextAction.deliver) {
				sumWeight -= nextAction.task.weight;
			}
			else {
				sumWeight += nextAction.task.weight;
			}
			if (sumWeight > capacity)
				return false;
		}

		return true;
	}

	public void getFirstSolution(List<Vehicle> vehicles, ArrayList<Task> taskList, ArrayList<ArrayList<Integer>> Solution){

		int place;
		int id_v;


		for (int i = 0; i < Solution.size(); i++) {
			Solution.get(i).clear();
		}

		for (int i = 0; i < taskList.size(); i++) {
			id_v = (int)(Math.random() * Solution.size());
			place = (int)(Math.random() * Solution.get(id_v).size());
			if ( place % 2 == 1)
				place -= 1;
			Solution.get(id_v).add(place, 2 * i + 1);   // Action = deliver the task
			Solution.get(id_v).add(place, 2 * i);		// Action = pickup the task

		}
		for ( int i = 0; i < vehicles.size();i++) {
			Solution.get(i).add(-1);  // set last Action of all vehicle to -1 (null)
		}
	}

	public ArrayList<ArrayList<Integer>> changeTask(ArrayList<ArrayList<Integer>> SolutionOld, int id_v1, int id_v2, int id_randomTask, int place_1, int place_2){
		ArrayList<ArrayList<Integer>> Solution = new ArrayList<ArrayList<Integer>>();

		Solution = copyList(SolutionOld);

		int indexOftaskPickup = SolutionOld.get(id_v1).indexOf(id_randomTask); // index of the action that consist to pickup the task
		int taskPickupId = SolutionOld.get(id_v1).get(indexOftaskPickup); //id of the action that consist to pickup the task
		int taskDeliveryId = taskPickupId + 1;  //id of the action that consist to deliver the task
		int indexOftaskDelivery = SolutionOld.get(id_v1).indexOf(taskDeliveryId); // index of the action that consist to deliver the task

		if(indexOftaskDelivery == place_2 && indexOftaskPickup == place_1) {
			return null;
		}

		Solution.get(id_v1).remove(indexOftaskDelivery);
		Solution.get(id_v1).remove(indexOftaskPickup);
		Solution.get(id_v2).add(place_1, taskPickupId);
		Solution.get(id_v2).add(place_2, taskDeliveryId);

		return Solution;
	}

	public ArrayList<ArrayList<ArrayList<Integer>>> chooseNeighbours(ArrayList<ArrayList<Integer>> SolutionOld, 
			List<Vehicle> vehicles, ArrayList<Task> taskList, int iterInLocalMinima){
		ArrayList<ArrayList<ArrayList<Integer>>> neighbours = new ArrayList<ArrayList<ArrayList<Integer>>>();
		ArrayList<ArrayList<Integer>> Solution = new ArrayList<ArrayList<Integer>>();

		int nbVehicle = vehicles.size();
		int id_v1;
		int nbMaxTaskChange = 3;
		int nbMaxTaskSwap = 1;
		int delay = 0;

		if(iterInLocalMinima >= MINITERTOBIGSWAP) { // Reduce the number of choice when trap into a minima ==> more chance to dig up
			nbMaxTaskChange = 1;
			nbMaxTaskSwap = 0;
		}

		for ( int count = 0; count < nbMaxTaskChange; count ++) {
			// Choose vehicle
			id_v1 = (int)(Math.random()*nbVehicle);
			while(SolutionOld.get(id_v1).get(0) == -1)
				id_v1 = (int)(Math.random()*nbVehicle);
			// Choose Task
			int randomTask = SolutionOld.get(id_v1).get((int)(Math.random()*SolutionOld.get(id_v1).size()-1));
			if (randomTask % 2 == 1)
				randomTask -= 1;
			// Change vehicle
			for (int id_v2 = 0; id_v2 < nbVehicle; id_v2++){
				if(id_v2 != id_v1) {
					for (int i = 0; i < SolutionOld.get(id_v2).size(); i++) {
						for (int j = i+1; j <SolutionOld.get(id_v2).size()+1;j++) {
							Solution = changeTask(SolutionOld, id_v1, id_v2, randomTask, i,j);
							if (Solution != null && isValid(Solution.get(id_v2), taskList, vehicles.get(id_v1).capacity())) {
								neighbours.add(Solution);
							}
						}
					}
				}
			}
		}
		
		if ( Math.random() < PROB2SEESWAP) {
			for ( int count = 0; count < nbMaxTaskSwap; count ++) {
				// Choose vehicle
				delay = 0;
				id_v1 = (int)(Math.random()*nbVehicle);
				while(SolutionOld.get(id_v1).size() <= 3 && delay < DELAYTOFINDSWAP){
					id_v1 = (int)(Math.random()*nbVehicle);
					delay ++;
				}
				// Choose Task
				int randomTask = SolutionOld.get(id_v1).get((int)(Math.random()*SolutionOld.get(id_v1).size()-1));
				if (randomTask % 2 == 1)
					randomTask -= 1;

				// Swap 
				for (int i = 0; i < SolutionOld.get(id_v1).size()-2; i++) {
					for (int j = i+1; j <SolutionOld.get(id_v1).size()-1;j++) {
						Solution = changeTask(SolutionOld, id_v1, id_v1, randomTask, i,j);
						if (Solution != null && isValid(Solution.get(id_v1), taskList, vehicles.get(id_v1).capacity())) {
							neighbours.add(Solution);
						}
					}
				}
			}
		}

		return neighbours;
	}

	public ArrayList<ArrayList<Integer>> localChoice(ArrayList<ArrayList<ArrayList<Integer>>> neighbours,
			ArrayList<ArrayList<Integer>> SolutionOld,
			List<Vehicle> vehicles,
			ArrayList<Task> taskList,
			int bestCost){

		
		if(neighbours.isEmpty())
			return SolutionOld;

		double p = 0.3;

		int newBestCost = getCost(neighbours.get(0),vehicles, taskList);
		int newCost;
		int id_best = 0;
		

		if (neighbours.size() >= 1){
			for(int i = 1; i < neighbours.size();i++) {
				newCost = getCost(neighbours.get(i),vehicles, taskList);
				if (newCost < newBestCost) {
					newBestCost = newCost;
					id_best = i;
				}
				else if (newCost == newBestCost) {
					id_best = Math.random()*2 < 1? id_best : i;
				}
			}
		}

		if(newBestCost < bestCost) {
			bestCost = newBestCost;
			return neighbours.get(id_best);
		}
		else if(Math.random() < PROB2DIGUP) {
			bestCost = newBestCost;
			return neighbours.get(id_best);
		}
		else {
			return SolutionOld;
		}

	}

	public int getCost(ArrayList<ArrayList<Integer>> List, List<Vehicle> vehicles, ArrayList<Task> taskList) {
		int totalCost = 0;
		int costPerKm;
		City currentCity = null;
		City nextCity = null;

		for(int i = 0; i < List.size(); i++) { // Vehicles
			costPerKm = vehicles.get(i).costPerKm();
			currentCity = vehicles.get(i).homeCity();
			for(int j = 0; j < List.get(i).size()-1; j++) { // Actions
				nextCity = id2Action(List.get(i).get(j), taskList).destination;
				totalCost += Measures.unitsToKM((long)currentCity.distanceUnitsTo(nextCity)) * costPerKm;
				currentCity = nextCity;
			}
		}
		return totalCost;
	}

	public List<Plan> centralizedplan(List<Vehicle> vehicles, TaskSet tasks, double timeAllowed, AtomicInteger finalCost) {
		
		
		
		if(tasks.isEmpty()) {
			ArrayList<Plan> plans = new ArrayList<Plan>();
			for (int i = 0; i < vehicles.size(); i++) {
				plans.add(new Plan(vehicles.get(i).getCurrentCity()));
			}
			finalCost.set(0);;
			return plans;
		}
		
		
		long time_start = System.currentTimeMillis();
		long time_now;
		long duration = 0;

		ArrayList<Task> taskList = new ArrayList<Task>(tasks);
		ArrayList<ArrayList<ArrayList<Integer>>> neighbours = new ArrayList<ArrayList<ArrayList<Integer>>>();

		ArrayList<ArrayList<Integer>> Solution = new ArrayList<ArrayList<Integer>>();
		ArrayList<ArrayList<Integer>> SolutionOld = new ArrayList<ArrayList<Integer>>();
		ArrayList<ArrayList<Integer>> bestSolution = new ArrayList<ArrayList<Integer>>();

		for (int i = 0; i < vehicles.size(); i++) {
			Solution.add(new ArrayList<Integer>());
		}

		int iter = 0;
		int iterInLocalMinima = 0;
		int iterWithoutImprovement = 0;
		int newCost = 0;
		int bestCost = 0;
		int localBestCost = 0;

		getFirstSolution(vehicles, taskList, Solution);

		while (iterWithoutImprovement < MAXITERWITHOUTIMPROV*taskList.size()/100 && duration < timeAllowed * (timeout_plan - TIMEOUTMARGE)) {

			
			SolutionOld = copyList(Solution);
			neighbours = chooseNeighbours(SolutionOld,vehicles,taskList, iterInLocalMinima); 
			
			
			Solution = localChoice(neighbours, SolutionOld,vehicles,taskList, bestCost);

			newCost = getCost(Solution, vehicles, taskList);

			if(iter == 0) {
				bestCost = newCost;
				localBestCost = bestCost;
				bestSolution = copyList(Solution);
			}
			else {
				if (newCost < bestCost) {
					bestCost = newCost;
					localBestCost = newCost;
					iterInLocalMinima = 0;
					iterWithoutImprovement = 0;
					bestSolution = copyList(Solution);
				}
				else{
					iterWithoutImprovement++;
					if (newCost < localBestCost){
						localBestCost = newCost;
						iterInLocalMinima = 0;
					}
					else if (newCost < localBestCost + RANGELOCALMINIMA){
						iterInLocalMinima ++;

					}
					else {
						iterInLocalMinima = 0;
						localBestCost = newCost;
					}
				}
			}
			
			time_now = System.currentTimeMillis();
			duration = time_now - time_start;
			
			//print("\niteration N°" + iter + " new Cost: " + newCost + " Iteration without improvement N°" + iterWithoutImprovement
			//		+" best Cost: " + bestCost + " after " + duration/60000 + " minutes and " + (duration/1000)%60 + " seconds ("+duration+" miliseconds)");
			
			iter++;
		}

		List<Plan> plans = getPlan(vehicles, taskList, bestSolution);

		//print("best cost overall: " + bestCost + " after " + iter + " iterations.");
		
		finalCost.set(bestCost);

		//print(plans);

		long time_end = System.currentTimeMillis();
		duration = time_end - time_start;
		//System.out.println("The plan was generated in " + duration + " milliseconds: " + duration/iter + "milliseconds/iteration");


		return plans;
	}
	private List<Plan> getPlan( List<Vehicle> vehicles, ArrayList<Task> taskList, ArrayList<ArrayList<Integer>> List) {
		
		List<Plan> plans = new ArrayList<Plan>();
		City currentCity = null;
		City nextCity = null;
		Action nextAction;

		for(int i = 0; i < List.size(); i++) { // Vehicles

			//print("vehicle: "+i);

			currentCity = vehicles.get(i).homeCity();
			Plan plan = new Plan(currentCity);
			for(int j = 0; j < List.get(i).size()-1; j++) { // Actions
				nextAction = id2Action(List.get(i).get(j), taskList);
				nextCity = nextAction.destination;

				for (City city : currentCity.pathTo(nextCity)) {
					plan.appendMove(city);
				}

				if(nextAction.deliver == false) {

					plan.appendPickup(nextAction.task);
					// print("pickup task: " + nextAction.task.id);

				}
				else {

					plan.appendDelivery(nextAction.task);

					//print("deliver task: " + nextAction.task.id);

				}
				currentCity = nextCity;
			}

			plans.add(plan);
		}
		return plans;
	}

	@SuppressWarnings("unchecked")
	private ArrayList<ArrayList<Integer>> copyList(ArrayList<ArrayList<Integer>> List2Copy) {
		ArrayList<ArrayList<Integer>> copy = new ArrayList<ArrayList<Integer>>();

		for ( int i = 0; i < List2Copy.size(); i++) {
			copy.add((ArrayList<Integer>) List2Copy.get(i).clone());
		}
		
		return copy;
	}
  
	private void print(Object o) {
		System.out.println(o);
	}
	
}



/*
 * compute with 30 repeat and task weight = 3
 * 
for number of task: 1
Final expectedCostPerKm: 9.96380979453384
Final expectedCostPerTask: 2346.6
Final expectedPathLength: 262.0

for number of task: 2
Final expectedCostPerKm: 6.828516681363128
Final expectedCostPerTask: 1611.85
Final expectedPathLength: 501.96666666666664

for number of task: 3
Final expectedCostPerKm: 6.157171489172086
Final expectedCostPerTask: 1389.7888888888892
Final expectedPathLength: 699.5333333333333

for number of task: 4
Final expectedCostPerKm: 5.356862262827594
Final expectedCostPerTask: 1364.8
Final expectedPathLength: 1057.4666666666667

for number of task: 5
Final expectedCostPerKm: 4.700089393074532
Final expectedCostPerTask: 1114.5866666666666
Final expectedPathLength: 1224.1

for number of task: 6
Final expectedCostPerKm: 4.342296929628071
Final expectedCostPerTask: 1024.7055555555555
Final expectedPathLength: 1441.4333333333334

for number of task: 7
Final expectedCostPerKm: 4.011541097808358
Final expectedCostPerTask: 1024.633333333333
Final expectedPathLength: 1797.3333333333333

for number of task: 8
Final expectedCostPerKm: 3.586746951759379
Final expectedCostPerTask: 858.5541666666667
Final expectedPathLength: 1964.0333333333333

for number of task: 9
Final expectedCostPerKm: 3.446015575266523
Final expectedCostPerTask: 839.988888888889
Final expectedPathLength: 2214.5666666666666

for number of task: 10
Final expectedCostPerKm: 3.173611124539923
Final expectedCostPerTask: 780.1366666666667
Final expectedPathLength: 2472.7

for number of task: 11
Final expectedCostPerKm: 2.96074285444407
Final expectedCostPerTask: 741.4242424242425
Final expectedPathLength: 2781.733333333333

for number of task: 12
Final expectedCostPerKm: 2.875796556018435
Final expectedCostPerTask: 713.3361111111112
Final expectedPathLength: 3012.9666666666667

for number of task: 13
Final expectedCostPerKm: 2.584580652277275
Final expectedCostPerTask: 652.351282051282
Final expectedPathLength: 3295.1

compute with 20 repeat:

for number of task: 14
Final expectedCostPerKm: 2.510605505245567
Final expectedCostPerTask: 627.6249999999998
Final expectedPathLength: 3555.2

for number of task: 15
Final expectedCostPerKm: 2.387070879861223
Final expectedCostPerTask: 586.7700000000001
Final expectedPathLength: 3732.6

for number of task: 16
Final expectedCostPerKm: 2.1889453066823403
Final expectedCostPerTask: 563.69375
Final expectedPathLength: 4139.95

for number of task: 17
Final expectedCostPerKm: 2.2120223471405094
Final expectedCostPerTask: 540.5764705882353
Final expectedPathLength: 4171.35

for number of task: 18
Final expectedCostPerKm: 2.168445119619664
Final expectedCostPerTask: 516.7722222222221
Final expectedPathLength: 4330.8

for number of task: 19
Final expectedCostPerKm: 2.058269239394419
Final expectedCostPerTask: 499.46842105263147
Final expectedPathLength: 4669.75
Final expectedPathLengthForOneTask: 245.77631578947367

for number of task: 20
Final expectedCostPerKm: 1.8992499717084068
Final expectedCostPerTask: 479.93250000000006
Final expectedPathLength: 5085.95
Final expectedPathLengthForOneTask: 254.29749999999999

 */

