package tel.discord.rtab.minigames;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class Gamble implements MiniGame {
	static final String NAME = "The Gamble";
	static final boolean BONUS = false;
	List<Integer> money = Arrays.asList(100,300,500,700,1000,3000,5000,7000,
			10000,20000,30000,40000,50000,70000,100000,200000,300000,400000,500000,1000000);
	boolean alive;
	boolean[] pickedSpaces;
	int lastPick;
	int lastSpace;
	int total;
	int baseMultiplier;
	
	@Override
	public LinkedList<String> initialiseGame(String channelID, int baseMultiplier)
	{
		this.baseMultiplier = baseMultiplier;
		LinkedList<String> output = new LinkedList<>();
		//Initialise the game
		alive = true;
		pickedSpaces = new boolean[money.size()];
		total = 0;
		lastPick = 0;
		for(int i=0; i<money.size(); i++)
			money.set(i, money.get(i)*baseMultiplier);
		Collections.shuffle(money);
		//Give instructions
		output.add("In The Gamble, your objective is to guess "
				+ "if the next space picked will be higher or lower than the one before.");
		output.add("Start by selecting one of the twenty spaces on the board.");
		output.add("Then, you can choose to either take the money revealed or pick another one.");
		output.add("If you pick another space, the amount revealed must be higher. If it isn't, you lose everything.");
		output.add("If it is higher, you can choose to stop and take both spaces, or continue to pick another one.");
		output.add("The spaces range from "+String.format("$%,d",100*baseMultiplier)
				+"to "+String.format("$%,d",1_000_000*baseMultiplier) + ", and if you're lucky and brave you can win more than "
				+String.format("$%,d",2_500_000*baseMultiplier) + "in total.");
		output.add("Best of luck! Pick your first space when you're ready.");
		output.add(generateBoard());
		return output;
	}
	
	public LinkedList<String> playNextTurn(String pick)
	{
		LinkedList<String> output = new LinkedList<>();
		if(pick.toUpperCase().equals("STOP") && total > 0)
		{
			alive = false;
			return output;
		}
		else if(!isNumber(pick))
		{
			//Definitely don't say anything for random strings
			return output;
		}
		if(!checkValidNumber(pick))
		{
			output.add("Invalid pick.");
			return output;
		}
		else
		{
			lastSpace = Integer.parseInt(pick)-1;
			pickedSpaces[lastSpace] = true;
			if(money.get(lastSpace) < lastPick)
			{
				alive = false;
				total = 0;
			}
			else
			{
				total += money.get(lastSpace);
			}
			lastPick = money.get(lastSpace);
			//Start printing output
			output.add(String.format("Space %d selected...",lastSpace+1));
			if(total != lastPick)
				output.add("...");
			if(alive)
			{
				output.add(String.format("$%,d!",lastPick));
				if(lastPick == 1000000*baseMultiplier)
				{
					output.add("You found the highest amount!");
					alive = false;
				}
				else
				{
					output.add("Choose another space if you dare, or type STOP to stop with your current total.");
					output.add(generateBoard());
				}
			}
			else
			{
				output.add(String.format("$%,d...",lastPick));
				output.add("Sorry, you lose.");
			}
			return output;
		}
	}
	
	boolean isNumber(String message)
	{
		try
		{
			Integer.parseInt(message);
			return true;
		}
		catch(NumberFormatException e1)
		{
			return false;
		}
	}
	
	boolean checkValidNumber(String message)
	{
		int location = Integer.parseInt(message)-1;
		return (location >= 0 && location < money.size() && !pickedSpaces[location]);
	}
	
	String generateBoard()
	{
		StringBuilder display = new StringBuilder();
		display.append("```\n");
		display.append("  THE GAMBLE  \n");
		for(int i=0; i<money.size(); i++)
		{
			if(pickedSpaces[i])
			{
				display.append("  ");
			}
			else
			{
				display.append(String.format("%02d",(i+1)));
			}
			if(i%5 == 4)
				display.append("\n");
			else
				display.append(" ");
		}
		display.append("\n");
		//Next display our total and last space picked
		display.append(String.format("Last pick: $%,d\n",lastPick));
		display.append(String.format("    Total: $%,d\n",total));
		display.append("```");
		return display.toString();
	}
	
	@Override
	public boolean isGameOver() {
		return !alive;
	}

	@Override
	public int getMoneyWon() {
		if(isGameOver())
			return total;
		else
			return 0;
	}

	@Override
	public boolean isBonusGame() {
		return BONUS;
	}
	
	@Override
	public String getBotPick()
	{
		//We don't need to check if we need to stop if we haven't even picked once yet
		if(lastPick > 0)
		{
			boolean willStop = false;
			//Get number of values lower than current pick
			int pickPosition = Arrays.binarySearch(money.toArray(),lastPick);
			int spacesPicked = 0;
			for(boolean next : pickedSpaces)
				if(next)
					spacesPicked ++;
			int badPicks = pickPosition + 1 - spacesPicked;
			//Basically take a "trial" pick and stop if it comes up bad
			willStop = (Math.random() * (money.size() - spacesPicked)) < badPicks;
			if(willStop)
				return "STOP";
		}
		//If we aren't going to stop, let's just pick our next space
		ArrayList<Integer> openSpaces = new ArrayList<>(money.size());
		for(int i=0; i<money.size(); i++)
			if(!pickedSpaces[i])
				openSpaces.add(i+1);
		return String.valueOf(openSpaces.get((int)(Math.random()*openSpaces.size())));
	}
	
	@Override
	public String toString()
	{
		return NAME;
	}
}
