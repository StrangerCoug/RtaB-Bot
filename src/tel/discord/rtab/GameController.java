package tel.discord.rtab;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.MessageChannel;
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import tel.discord.rtab.enums.BlammoChoices;
import tel.discord.rtab.enums.BombType;
import tel.discord.rtab.enums.Events;
import tel.discord.rtab.enums.GameStatus;
import tel.discord.rtab.enums.Games;
import tel.discord.rtab.enums.MoneyMultipliersToUse;
import tel.discord.rtab.enums.PlayerJoinReturnValue;
import tel.discord.rtab.enums.PlayerQuitReturnValue;
import tel.discord.rtab.enums.PlayerStatus;
import tel.discord.rtab.enums.SpaceType;
import tel.discord.rtab.minigames.MiniGame;
import tel.discord.rtab.minigames.SuperBonusRound;

import com.jagrosh.jdautilities.commons.waiter.EventWaiter;

public class GameController
{
	static int boardSize = 15;
	public static MessageChannel channel = null;
	static List<Player> players = new ArrayList<>();
	static List<Player> winners = new ArrayList<>();
	static int currentTurn = -1;
	static int repeatTurn = 0;
	public static int playersJoined = 0;
	static int playersAlive = 0;
	static ListIterator<Games> gamesToPlay;
	public static GameStatus gameStatus = GameStatus.SIGNUPS_OPEN;
	static boolean[] pickedSpaces;
	static int spacesLeft;
	static boolean[] bombs;
	static Board gameboard;
	public static EventWaiter waiter;
	public static Timer timer = new Timer();
	static Message waitingMessage;

	private static class StartGameTask extends TimerTask
	{
		@Override
		public void run()
		{
			startTheGameAlready();
		}
	}
	private static class FinalCallTask extends TimerTask
	{
		@Override
		public void run()
		{
			channel.sendMessage("Thirty seconds before game starts!").queue();
			channel.sendMessage(listPlayers(false)).queue();
		}
	}
	private static class ClearMinigameQueueTask extends TimerTask
	{
		@Override
		public void run()
		{
			runNextMiniGame();
		}
	}
	
	/*
	 * reset - (re)initialises the game state by removing all players and clearing the board.
	 */
	public static void reset()
	{
		players.clear();
		currentTurn = -1;
		playersJoined = 0;
		playersAlive = 0;
		gameStatus = GameStatus.SIGNUPS_OPEN;
		gameboard = null;
		repeatTurn = 0;
	}
	/*
	 * addPlayer - adds a player to the game, or updates their name if they're already in.
	 * MessageChannel channelID - channel the request took place in (only used to know where to send game details to)
	 * String playerID - ID of player to be added.
	 * Returns an enum which gives the result of the join attempt.
	 */
	public static PlayerJoinReturnValue addPlayer(MessageChannel channelID, Member playerID)
	{
		//Make sure game isn't already running
		if(gameStatus != GameStatus.SIGNUPS_OPEN)
			return PlayerJoinReturnValue.INPROGRESS;
		//Are they in the right channel?
		if(playersJoined == 0)
		{
			//If first player, this is the channel, now queue up starting the game
			channel = channelID;
			timer.schedule(new FinalCallTask(),  90000);
			timer.schedule(new StartGameTask(), 120000);
		}
		else if(channel != channelID)
			return PlayerJoinReturnValue.WRONGCHANNEL;
		//Create player object
		Player newPlayer = new Player(playerID);
		if(newPlayer.name.contains(":") || newPlayer.name.startsWith("#") || newPlayer.name.startsWith("!"))
			return PlayerJoinReturnValue.BADNAME;
		//Dumb easter egg
		if(newPlayer.money <= -1000000000)
			return PlayerJoinReturnValue.ELIMINATED;
		//Look for match already in player list
		for(int i=0; i<playersJoined; i++)
		{
			if(players.get(i).uID.equals(newPlayer.uID))
			{
				//Found them, check if we should update their name or just laugh at them
				if(players.get(i).name == newPlayer.name)
					return PlayerJoinReturnValue.ALREADYIN;
				else
				{
					players.set(i,newPlayer);
					return PlayerJoinReturnValue.UPDATED;
				}
			}
		}
		//Haven't found one, add them to the list
		players.add(newPlayer);
		playersJoined++;
		if(playersJoined == 1)
			return PlayerJoinReturnValue.CREATED;
		else
			return PlayerJoinReturnValue.JOINED;
	}
	/*
	 * removePlayer - removes a player from the game.
	 * MessageChannel channelID - channel the request was registered in.
	 * String playerID - ID of player to be removed.
	 */
	public static PlayerQuitReturnValue removePlayer(MessageChannel channelID, User playerID)
	{
		//Make sure game isn't running, too late to quit now
		if(gameStatus != GameStatus.SIGNUPS_OPEN)
			return PlayerQuitReturnValue.GAMEINPROGRESS;
		//Search for player
		for(int i=0; i<playersJoined; i++)
		{
			if(players.get(i).uID.equals(playerID.getId()))
			{
				//Found them, get rid of them and call it a success
				players.remove(i);
				playersJoined --;
				return PlayerQuitReturnValue.SUCCESS;
			}
		}
		//Didn't find them, why are they trying to quit in the first place?
		return PlayerQuitReturnValue.NOTINGAME;
	}
	/*
	 * startTheGameAlready - prompts for players to choose bombs.
	 */
	public static void startTheGameAlready()
	{
		//If the game's already running, just don't
		if(gameStatus != GameStatus.SIGNUPS_OPEN)
		{
			return;
		}
		if(playersJoined < 2)
		{
			//Didn't get players, abort
			channel.sendMessage("Not enough players. Aborting game.").queue();
			reset();
			return;
		}
		//Declare game in progress so we don't get latecomers
		channel.sendMessage("Starting game...").queue();
		gameStatus = GameStatus.IN_PROGRESS;
		//Initialise stuff that needs initialising
		boardSize = 5 + (5*playersJoined);
		spacesLeft = boardSize;
		pickedSpaces = new boolean[boardSize];
		bombs = new boolean[boardSize];
		//Get the "waiting on" message going
		waitingMessage = channel.sendMessage(listPlayers(true)).complete();
		//Request players send in bombs, and set up waiter for them to return
		for(int i=0; i<playersJoined; i++)
		{
			final int iInner = i;
			players.get(iInner).user.openPrivateChannel().queue(
					(channel) -> channel.sendMessage("Please PM your bomb by sending a number 1-" + boardSize).queue());
			waiter.waitForEvent(MessageReceivedEvent.class,
					//Check if right player, and valid bomb pick
					e -> (e.getAuthor().equals(players.get(iInner).user)
							&& checkValidNumber(e.getMessage().getContentRaw())),
					//Parse it and update the bomb board
					e -> 
					{
						bombs[Integer.parseInt(e.getMessage().getContentRaw())-1] = true;
						players.get(iInner).user.openPrivateChannel().queue(
								(channel) -> channel.sendMessage("Bomb placement confirmed.").queue());
						players.get(iInner).status = PlayerStatus.ALIVE;
						playersAlive ++;
						checkReady();
					},
					//Or timeout after a minute
					1, TimeUnit.MINUTES, () ->
					{
						gameStatus = GameStatus.SIGNUPS_OPEN;
						checkReady();
					});
		}

	}
	static void checkReady()
	{
		if(gameStatus == GameStatus.SIGNUPS_OPEN)
		{
			channel.sendMessage("Bomb placement timed out. Game aborted.").queue();
			reset();
		}
		else
		{
			//If everyone has sent in, what are we waiting for?
			if(playersAlive == playersJoined)
			{
				//Delete the "waiting on" message
				waitingMessage.delete().queue();
				//Determine first player and player order
				currentTurn = (int)(Math.random()*playersJoined);
				gameboard = new Board(boardSize);
				Collections.shuffle(players);
				//Let's get things rolling!
				channel.sendMessage("Let's go!").queue();
				runTurn();
			}
			//If they haven't, update the message to tell us who we're still waiting on
			else
			{
				waitingMessage.editMessage(listPlayers(true)).queue();
			}
		}
	}
	static void runTurn()
	{
		if(repeatTurn > 0)
		{
			repeatTurn --;
			channel.sendMessage(players.get(currentTurn).user.getAsMention() + ", pick again.")
				.completeAfter(3,TimeUnit.SECONDS);
		}
		else
		{
			channel.sendMessage(players.get(currentTurn).user.getAsMention() + ", your turn. Choose a space on the board.")
				.completeAfter(3,TimeUnit.SECONDS);
		}
		displayBoardAndStatus(true, false);
		waiter.waitForEvent(MessageReceivedEvent.class,
				//Right player and channel
				e ->
				{
					if(e.getAuthor().equals(players.get(currentTurn).user) && e.getChannel().equals(channel)
							&& checkValidNumber(e.getMessage().getContentRaw()))
					{
							int location = Integer.parseInt(e.getMessage().getContentRaw());
							if(pickedSpaces[location-1])
							{
								channel.sendMessage("That space has already been picked.").queue();
								return false;
							}
							else
								return true;
					}
					return false;
				},
				//Parse it and call the method that does stuff
				e -> 
				{
					int location = Integer.parseInt(e.getMessage().getContentRaw())-1;
					resolveTurn(location);
				});
	}
	static void resolveTurn(int location)
	{
		pickedSpaces[location] = true;
		spacesLeft--;
		channel.sendMessage("Space " + (location+1) + " selected...").completeAfter(1,TimeUnit.SECONDS);
		if(players.get(currentTurn).threshold)
		{
			players.get(currentTurn).addMoney(50000,MoneyMultipliersToUse.NOTHING);
			channel.sendMessage("(-$50,000)").queueAfter(1,TimeUnit.SECONDS);
		}
		if(bombs[location])
		{
			runBombLogic(location);
		}
		else
		{
			runSafeLogic(location);
		}
	}
	static void runEndTurnLogic()
	{
		//Test if game over
		if(spacesLeft <= 0 || playersAlive == 1)
		{
			gameStatus = GameStatus.END_GAME;
			if(spacesLeft < 0)
				channel.sendMessage("An error has occurred, ending the game, @Atia#2084 fix pls").queue();
			channel.sendMessage("Game Over.").completeAfter(3,TimeUnit.SECONDS);
			runNextEndGamePlayer();
		}
		else
		{
			//Advance turn to next player if there isn't a repeat going
			if(repeatTurn == 0)
				advanceTurn(false);
			runTurn();
		}
	}
	static void runBombLogic(int location)
	{
		channel.sendMessage("...").completeAfter(5,TimeUnit.SECONDS);
		channel.sendMessage("It's a **BOMB**.").completeAfter(5,TimeUnit.SECONDS);
		//If player has a joker, force it to not explode
		//This is a really ugly way of doing it though
		if(players.get(currentTurn).jokers > 0)
		{
			channel.sendMessage("But you have a joker!").queueAfter(2,TimeUnit.SECONDS);
			players.get(currentTurn).jokers --;
			gameboard.bombBoard[location] = BombType.DUD;
		}
		//But is it a special bomb?
		StringBuilder extraResult = null;
		int penalty = Player.BOMB_PENALTY;
		if(players.get(currentTurn).newbieProtection > 0)
			penalty = Player.NEWBIE_BOMB_PENALTY;
		switch(gameboard.bombBoard[location])
		{
		case NORMAL:
			channel.sendMessage(String.format("It goes **BOOM**. $%,d lost as penalty.",Math.abs(penalty)))
				.completeAfter(5,TimeUnit.SECONDS);
			extraResult = players.get(currentTurn).blowUp(1,false);
			break;
		case BANKRUPT:
			int amountLost = players.get(currentTurn).bankrupt();
			if(amountLost == 0)
			{
				channel.sendMessage(String.format("It goes **BOOM**. $%,d lost as penalty.",Math.abs(penalty)))
					.completeAfter(5,TimeUnit.SECONDS);
			}
			else
			{
				channel.sendMessage("It goes **BOOM**...")
						.completeAfter(5,TimeUnit.SECONDS);
				channel.sendMessage("It also goes **BANKRUPT**. _\\*whoosh*_")
						.completeAfter(5,TimeUnit.SECONDS);
				if(amountLost < 0)
				{
					channel.sendMessage(String.format("**$%1$,d** *returned*, plus $%2$,d penalty.",
							Math.abs(amountLost),Math.abs(penalty)))
							.completeAfter(3,TimeUnit.SECONDS);
				}
				else
				{
					channel.sendMessage(String.format("**$%1$,d** lost, plus $%2$,d penalty.",
							amountLost,Math.abs(penalty)))
							.completeAfter(3,TimeUnit.SECONDS);
				}
			}
			extraResult = players.get(currentTurn).blowUp(1,false);
			break;
		case BOOSTHOLD:
			StringBuilder resultString = new StringBuilder().append("It ");
			if(players.get(currentTurn).booster != 100)
				resultString.append("holds your boost, then ");
			resultString.append(String.format("goes **BOOM**. $%,d lost as penalty.",Math.abs(penalty)));
			channel.sendMessage(resultString)
					.completeAfter(5,TimeUnit.SECONDS);
			extraResult = players.get(currentTurn).blowUp(1,true);
			break;
		case CHAIN:
			channel.sendMessage("It goes **BOOM**...")
					.completeAfter(5,TimeUnit.SECONDS);
			int chain = 1;
			do
			{
				chain *= 2;
				StringBuilder nextLevel = new StringBuilder();
				nextLevel.append("**");
				for(int i=0; i<chain; i++)
				{
					nextLevel.append("BOOM");
					if(i+1 < chain)
						nextLevel.append(" ");
				}
				nextLevel.append("**");
				if(chain < 8)
					nextLevel.append("...");
				else
					nextLevel.append("!!!");
				channel.sendMessage(nextLevel).completeAfter(5,TimeUnit.SECONDS);
			}
			while(Math.random() * chain < 1);
			channel.sendMessage(String.format("**$%,d** penalty!",chain*penalty))
					.completeAfter(5,TimeUnit.SECONDS);
			extraResult = players.get(currentTurn).blowUp(chain,false);
			break;
		case DUD:
			channel.sendMessage("It goes _\\*fizzle*_.")
					.completeAfter(5,TimeUnit.SECONDS);
			break;
		}
		if(extraResult != null)
			channel.sendMessage(extraResult).queue();
		runEndTurnLogic();
	}
	static void runSafeLogic(int location)
	{
		//Always trigger it on a blammo, otherwise based on spaces left and players in game
		if((Math.random()*spacesLeft)<playersJoined || gameboard.typeBoard[location] == SpaceType.BLAMMO)
			channel.sendMessage("...").completeAfter(5,TimeUnit.SECONDS);
		//Figure out what space we got
		StringBuilder resultString = new StringBuilder();
		StringBuilder extraResult = null;
		switch(gameboard.typeBoard[location])
		{
		case CASH:
			//On cash, update the player's score and tell them how much they won
			int cashWon = gameboard.cashBoard[location].getValue();
			resultString.append("**");
			if(cashWon<0)
				resultString.append("-");
			resultString.append("$");
			resultString.append(String.format("%,d",Math.abs(cashWon)));
			resultString.append("**");
			extraResult = players.get(currentTurn).addMoney(cashWon, MoneyMultipliersToUse.BOOSTER_ONLY);
			break;
		case BOOSTER:
			//On cash, update the player's booster and tell them what they found
			int boostFound = gameboard.boostBoard[location].getValue();
			resultString.append("A **" + String.format("%+d",boostFound) + "%** Booster");
			if(boostFound > 0)
				resultString.append("!");
			else
				resultString.append(".");
			players.get(currentTurn).addBooster(boostFound);
			break;
		case GAME:
			//On a game, announce it and add it to their game pile
			Games gameFound = gameboard.gameBoard[location];
			resultString.append("It's a minigame, **" + gameFound + "**!");
			players.get(currentTurn).games.add(gameFound);
			players.get(currentTurn).games.sort(null);
			break;
		case EVENT:
			activateEvent(gameboard.eventBoard[location]);
			return;
		case BLAMMO:
			channel.sendMessage("It's a **BLAMMO!** Quick, press a button!").completeAfter(5,TimeUnit.SECONDS);
			channel.sendMessage("```\nBLAMMO\n 1  2 \n 3  4 \n```").queue();
			waiter.waitForEvent(MessageReceivedEvent.class,
					//Right player and channel
					e ->
					{
						return (e.getAuthor().equals(players.get(currentTurn).user) && e.getChannel().equals(channel)
								&& checkValidNumber(e.getMessage().getContentRaw()) 
										&& Integer.parseInt(e.getMessage().getContentRaw()) <= 4);
					},
					//Parse it and call the method that does stuff
					e -> 
					{
						int button = Integer.parseInt(e.getMessage().getContentRaw())-1;
						runBlammo(button);
					});
			return;
		}
		channel.sendMessage(resultString).completeAfter(5,TimeUnit.SECONDS);
		if(extraResult != null)
			channel.sendMessage(extraResult).queue();
		runEndTurnLogic();
	}
	private static void runBlammo(int buttonPressed)
	{
		//Yes I know it's generating the result after they've already picked
		//But that's the sort of thing a blammo would do so I'm fine with it
		List<BlammoChoices> buttons = Arrays.asList(BlammoChoices.values());
		Collections.shuffle(buttons);
		channel.sendMessage("Button " + (buttonPressed+1) + " pressed...").queue();
		channel.sendMessage("...").completeAfter(3,TimeUnit.SECONDS);
		StringBuilder extraResult = null;
		int penalty = Player.BOMB_PENALTY;
		switch(buttons.get(buttonPressed))
		{
		case BLOCK:
			channel.sendMessage("You BLOCKED the BLAMMO!").completeAfter(3,TimeUnit.SECONDS);
			break;
		case ELIM_OPP:
			channel.sendMessage("You ELIMINATED YOUR OPPONENT!").completeAfter(3,TimeUnit.SECONDS);
			advanceTurn(false);
			if(players.get(currentTurn).newbieProtection > 0)
				penalty = Player.NEWBIE_BOMB_PENALTY;
			channel.sendMessage("Goodbye, " + players.get(currentTurn).user.getAsMention()
					+ String.format("! $%,d penalty!",Math.abs(penalty*4))).queue();
			if(repeatTurn > 0)
				channel.sendMessage("(You also negated the repeat!)").queue();
			extraResult = players.get(currentTurn).blowUp(4,false);
			break;
		case THRESHOLD:
			if(players.get(currentTurn).threshold)
			{
				//You already have a threshold situation? Buh-bye.
				channel.sendMessage("It's a THRESHOLD SITUATION, but you're already in one, so...")
					.completeAfter(3,TimeUnit.SECONDS);
			}
			else
			{
				channel.sendMessage("You're entering a THRESHOLD SITUATION!").completeAfter(3,TimeUnit.SECONDS);
				channel.sendMessage("You'll lose $50,000 for every pick you make, "
						+ "and if you lose the penalty will be four times as large!").queue();
				players.get(currentTurn).threshold = true;
				break;
			}
		case ELIM_YOU:
			channel.sendMessage("You ELIMINATED YOURSELF!").completeAfter(3,TimeUnit.SECONDS);
			if(players.get(currentTurn).newbieProtection > 0)
				penalty = Player.NEWBIE_BOMB_PENALTY;
			channel.sendMessage(String.format("$%,d penalty!",Math.abs(penalty*4))).queue();
			extraResult = players.get(currentTurn).blowUp(4,false);
			break;
		}
		if(extraResult != null)
			channel.sendMessage(extraResult).queue();
		runEndTurnLogic();
	}
	static void activateEvent(Events event)
	{
		switch(event)
		{
		case BOOST_DRAIN:
			channel.sendMessage("It's a **Boost Drain**, which cuts your booster in half...")
				.completeAfter(5,TimeUnit.SECONDS);
			players.get(currentTurn).booster /= 2;
			if(players.get(currentTurn).booster < Player.MIN_BOOSTER)
				players.get(currentTurn).booster = Player.MIN_BOOSTER;
			break;
		case MINEFIELD:
			channel.sendMessage("Oh no, it's a **Minefield**! Adding up to " + playersJoined + " more bombs...")
				.completeAfter(5,TimeUnit.SECONDS);
			for(int i=0; i<playersJoined; i++)
				bombs[(int)(Math.random()*boardSize)] = true;
			break;
		case LOCKDOWN:
			channel.sendMessage("It's a **Lockdown**, all non-bomb spaces on the board are now becoming cash!")
				.completeAfter(5,TimeUnit.SECONDS);
			for(int i=0; i<boardSize; i++)
			{
				//Blammos aren't affected
				if(gameboard.typeBoard[i] != SpaceType.BLAMMO)
					gameboard.typeBoard[i] = SpaceType.CASH;
			}
			break;
		case STARMAN:
			channel.sendMessage("Hooray, it's a **Starman**, here to destroy all the bombs!")
				.completeAfter(5,TimeUnit.SECONDS);
			for(int i=0; i<boardSize; i++)
				if(bombs[i] && !pickedSpaces[i])
				{
					channel.sendMessage("Bomb in space " + (i+1) + " destroyed.")
						.queueAfter(2,TimeUnit.SECONDS);
					pickedSpaces[i] = true;
					spacesLeft --;
				}
			break;
		case REPEAT:
			channel.sendMessage("It's a **Repeat**, you need to pick two more spaces in a row!")
				.completeAfter(5,TimeUnit.SECONDS);
			repeatTurn += 2;
			break;
		case JOKER:
			channel.sendMessage("Congratulations, you found a **Joker**, protecting you from a single bomb!")
				.completeAfter(5,TimeUnit.SECONDS);
			players.get(currentTurn).jokers ++;
			break;
		case GAME_LOCK:
			channel.sendMessage("It's a **Minigame Lock**, you'll get to play any minigames you have even if you bomb!")
				.completeAfter(5,TimeUnit.SECONDS);
			players.get(currentTurn).minigameLock = true;
			break;
		case SPLIT_SHARE:
			channel.sendMessage("It's **Split & Share**, "
					+ "don't lose the round now or you'll lose 10% of your total, "
					+ "approximately $" + String.format("%,d",(players.get(currentTurn).money/10)) + "!")
				.completeAfter(5,TimeUnit.SECONDS);
			players.get(currentTurn).splitAndShare = true;
			break;
		case JACKPOT:
			channel.sendMessage("You found the $25,000,000 **JACKPOT**, win the round to claim it!")
				.completeAfter(5,TimeUnit.SECONDS);
			players.get(currentTurn).jackpot = true;
			break;
		case BONUSP1:
			channel.sendMessage("It's a **+1 Bonus Multiplier**!").completeAfter(5,TimeUnit.SECONDS);
			players.get(currentTurn).winstreak += 1;
			break;
		case BONUSP2:
			channel.sendMessage("It's a **+2 Bonus Multiplier**!").completeAfter(5,TimeUnit.SECONDS);
			players.get(currentTurn).winstreak += 2;
			break;
		case BONUSP3:
			channel.sendMessage("It's a **+3 Bonus Multiplier**!").completeAfter(5,TimeUnit.SECONDS);
			players.get(currentTurn).winstreak += 3;
			break;
		case BLAMMO_FRENZY:
			channel.sendMessage("It's a **Blammo Frenzy**, good luck!!")
				.completeAfter(5,TimeUnit.SECONDS);
			for(int i=0; i<boardSize; i++)
			{
				//Switch cash to blammo with 1/3 chance
				if(gameboard.typeBoard[i] == SpaceType.CASH && Math.random()*3 < 1)
					gameboard.typeBoard[i] = SpaceType.BLAMMO;
			}
			break;
		}
		runEndTurnLogic();
	}
	static void runNextEndGamePlayer()
	{
		//Are there any winners left to loop through?
		advanceTurn(true);
		//If we're out of people to run endgame stuff with, get outta here after displaying the board
		if(currentTurn == -1)
		{
			saveData();
			players.sort(null);
			displayBoardAndStatus(false, true);
			reset();
			if(winners.size() > 0)
			{
				//Got a single winner, crown them!
				if(winners.size() == 1)
				{
					for(int i=0; i<3; i++)
						channel.sendMessage(winners.get(0).name + " WINS RACE TO A BILLION!")
							.completeAfter(2,TimeUnit.SECONDS);
					gameStatus = GameStatus.SEASON_OVER;
					runNextMiniGameTurn(new SuperBonusRound());
				}
				//Hold on, we have *multiple* winners? ULTIMATE SHOWDOWN HYPE
				else
				{
					//Tell them what's happening
					StringBuilder announcementText = new StringBuilder();
					for(Player next : winners)
					{
						next.resetPlayer();
						announcementText.append(next.user.getAsMention() + ", ");
					}
					announcementText.append("you have reached the goal together.");
					channel.sendMessage(announcementText.toString()).completeAfter(5,TimeUnit.SECONDS);
					channel.sendMessage("BUT THERE CAN BE ONLY ONE.").completeAfter(5,TimeUnit.SECONDS);
					channel.sendMessage("PREPARE FOR THE FINAL SHOWDOWN!").completeAfter(5,TimeUnit.SECONDS);
					//Prepare the game
					players.addAll(winners);
					winners.clear();
					playersJoined = players.size();
					startTheGameAlready();
				}
			}
			return;
		}
		//No? Good. Let's get someone to reward!
		//If they're a winner, boost their winstreak (folded players don't get this)
		if(players.get(currentTurn).status == PlayerStatus.ALIVE)
		{
			channel.sendMessage(players.get(currentTurn).user.getAsMention() + " Wins!")
				.completeAfter(1,TimeUnit.SECONDS);
			//Boost winstreak by number of opponents defeated
			players.get(currentTurn).winstreak += (playersJoined - playersAlive);
		}
		//Now the winstreak is right, we can display the board
		displayBoardAndStatus(false, false);
		//Check to see if any bonus games have been unlocked - folded players get this too
		//Search every multiple of 5 to see if we've got it
		for(int i=5; i<=players.get(currentTurn).winstreak;i+=5)
		{
			if(players.get(currentTurn).oldWinstreak < i)
				switch(i)
				{
				case 5:
					players.get(currentTurn).games.add(Games.SUPERCASH);
					break;
				case 10:
					players.get(currentTurn).games.add(Games.DIGITAL_FORTRESS);
					break;
				case 15:
					players.get(currentTurn).games.add(Games.SPECTRUM);
					break;
				case 20:
				default:
					players.get(currentTurn).games.add(Games.HYPERCUBE);
					break;
				}
		}
		//If they're a winner, give them a win bonus (folded players don't get this)
		if(players.get(currentTurn).status == PlayerStatus.ALIVE)
		{
			//Award $20k for each space picked, double it if every space was picked, then share with everyone in
			int winBonus = 20000*(boardSize-spacesLeft);
			if(spacesLeft <= 0)
				winBonus *= 2;
			winBonus /= playersAlive;
			if(spacesLeft <= 0 && playersAlive == 1)
				channel.sendMessage("**SOLO BOARD CLEAR!**").queue();
			channel.sendMessage(players.get(currentTurn).name + " receives a win bonus of **$"
					+ String.format("%,d",winBonus) + "**.").queue();
			StringBuilder extraResult = null;
			extraResult = players.get(currentTurn).addMoney(winBonus,MoneyMultipliersToUse.BOOSTER_AND_BONUS);
			if(extraResult != null)
				channel.sendMessage(extraResult).queue();
			//Don't forget about the jackpot
			if(players.get(currentTurn).jackpot)
			{
				channel.sendMessage("You won the $25,000,000 **JACKPOT**!").queue();
				players.get(currentTurn).addMoney(25000000,MoneyMultipliersToUse.NOTHING);
			}
		}
		//Then, folded or not, play out any minigames they've won
		if(players.get(currentTurn).status == PlayerStatus.FOLDED)
			players.get(currentTurn).status = PlayerStatus.OUT;
		else
			players.get(currentTurn).status = PlayerStatus.DONE;
		gamesToPlay = players.get(currentTurn).games.listIterator(0);
		timer.schedule(new ClearMinigameQueueTask(), 1000);
	}
	static void runNextMiniGame()
	{
		if(gamesToPlay.hasNext())
		{
			//Get the minigame
			Games nextGame = gamesToPlay.next();
			MiniGame currentGame = nextGame.getGame();
			StringBuilder gameMessage = new StringBuilder();
			gameMessage.append(players.get(currentTurn).user.getAsMention());
			if(currentGame.isBonusGame())
				gameMessage.append(", you've unlocked a bonus game: ");
			else
				gameMessage.append(", time for your next minigame: ");
			gameMessage.append(nextGame + "!");
			channel.sendMessage(gameMessage).queue();
			runNextMiniGameTurn(currentGame);
		}
		else
		{
			//Check for winning the game
			if(players.get(currentTurn).money >= 1000000000 && players.get(currentTurn).status == PlayerStatus.DONE)
			{
				winners.add(players.get(currentTurn));
			}
			runNextEndGamePlayer();
		}
	}
	static void runNextMiniGameTurn(MiniGame currentGame)
	{
		//Keep printing output until it runs out of output
		LinkedList<String> result = currentGame.getNextOutput();
		ListIterator<String> output = result.listIterator(0);
		while(output.hasNext())
		{
			channel.sendMessage(output.next()).completeAfter(2,TimeUnit.SECONDS);
		}
		//Check if the game's over
		if(currentGame.isGameOver())
		{
			completeMiniGame(currentGame);
			return;
		}
		//If it isn't, let's get more input to give it
		waiter.waitForEvent(MessageReceivedEvent.class,
				//Right player and channel
				e ->
				{
					return (e.getChannel().equals(channel) && e.getAuthor().equals(players.get(currentTurn).user));
				},
				//Parse it and call the method that does stuff
				e -> 
				{
					String miniPick = e.getMessage().getContentRaw();
					currentGame.sendNextInput(miniPick);
					runNextMiniGameTurn(currentGame);
				});
	}
	static void completeMiniGame(MiniGame currentGame)
	{
		//Cool, game's over now, let's grab their winnings
		int moneyWon = currentGame.getMoneyWon();
		//Only the Super Bonus Round will do this
		if(moneyWon == -1000000000)
			return;
		int multiplier = 1;
		//Did they have multiple copies of the game?
		while(gamesToPlay.hasNext())
		{
			//Move the iterator back one, to the first instance of the game
			gamesToPlay.previous();
			//If it matches (ie multiple copies), remove one and add it to the multiplier
			if(gamesToPlay.next() == gamesToPlay.next())
			{
				multiplier++;
				gamesToPlay.remove();
			}
			//Otherwise we'd better out it back where it belongs
			else
			{
				gamesToPlay.previous();
				break;
			}
		}
		StringBuilder resultString = new StringBuilder();
		resultString.append(String.format("Game Over. You won **$%,d**",moneyWon));
		if(multiplier > 1)
			resultString.append(String.format(" times %d copies!",multiplier));
		else
			resultString.append(".");
		StringBuilder extraResult = null;
		//Bypass the usual method if it's a bonus game so we don't have booster or winstreak applied
		if(currentGame.isBonusGame())
			players.get(currentTurn).addMoney(moneyWon*multiplier,MoneyMultipliersToUse.NOTHING);
		else
			extraResult = players.get(currentTurn).addMoney((moneyWon*multiplier),MoneyMultipliersToUse.BOOSTER_AND_BONUS);
		channel.sendMessage(resultString).queue();
		if(extraResult != null)
			channel.sendMessage(extraResult).queue();
		//Off to the next minigame! (After clearing the queue)
		timer.schedule(new ClearMinigameQueueTask(), 1000);
	}
	static void advanceTurn(boolean endGame)
	{
		//Keep spinning through until we've got someone who's still in the game, or until we've checked everyone
		int triesLeft = playersJoined;
		boolean isPlayerGood = false;
		do
		{
			currentTurn++;
			triesLeft --;
			currentTurn = currentTurn % playersJoined;
			//Is this player someone allowed to play now?
			switch(players.get(currentTurn).status)
			{
			case ALIVE:
				isPlayerGood = true;
				break;
			case FOLDED:
				if(endGame)
					isPlayerGood = true;
				break;
			default:
				break;
			}
		}
		while(!isPlayerGood && triesLeft > 0);
		//If we've checked everyone and no one is suitable anymore, whatever
		if(triesLeft == 0 && !isPlayerGood)
			currentTurn = -1;
	}
	static boolean checkValidNumber(String message)
	{
		try
		{
			int location = Integer.parseInt(message);
			return (location > 0 && location <= boardSize);
		}
		catch(NumberFormatException e1)
		{
			return false;
		}
	}
	public static void displayBoardAndStatus(boolean printBoard, boolean totals)
	{
		if(gameStatus == GameStatus.SIGNUPS_OPEN)
		{
			//No board to display if the game isn't running!
			return;
		}
		StringBuilder board = new StringBuilder().append("```\n");
		//Board doesn't need to be displayed if game is over
		if(printBoard)
		{
			board.append("     RtaB     \n");
			for(int i=0; i<boardSize; i++)
			{
				if(pickedSpaces[i])
				{
					board.append("  ");
				}
				else
				{
					board.append(String.format("%02d",(i+1)));
				}
				if(i%5==4)
					board.append("\n");
				else
					board.append(" ");
			}
			board.append("\n");
		}
		//Next the status line
		//Start by getting the lengths so we can pad the status bars appropriately
		//Add one extra to name length because we want one extra space between name and cash
		int nameLength = players.get(0).name.length();
		for(int i=1; i<playersJoined; i++)
			nameLength = Math.max(nameLength,players.get(i).name.length());
		nameLength ++;
		//And ignore the negative sign if there is one
		int moneyLength;
		if(totals)
		{
			moneyLength = String.valueOf(Math.abs(players.get(0).money)).length();
			for(int i=1; i<playersJoined; i++)
				moneyLength = Math.max(moneyLength, String.valueOf(Math.abs(players.get(i).money)).length());
		}
		else
		{
			moneyLength = String.valueOf(Math.abs(players.get(0).money-players.get(0).oldMoney)).length();
			for(int i=1; i<playersJoined; i++)
				moneyLength = Math.max(moneyLength,
						String.valueOf(Math.abs(players.get(i).money-players.get(i).oldMoney)).length());		
		}
		//Make a little extra room for the commas
		moneyLength += (moneyLength-1)/3;
		//Then start printing - including pointer if currently their turn
		for(int i=0; i<playersJoined; i++)
		{
			if(currentTurn == i)
				board.append("> ");
			else
				board.append("  ");
			board.append(String.format("%-"+nameLength+"s",players.get(i).name));
			//Now figure out if we need a negative sign, a space, or neither
			int playerMoney = (players.get(i).money - players.get(i).oldMoney);
			//What sign to print?
			if(playerMoney<0)
				board.append("-");
			else
				board.append("+");
			//Then print the money itself
			board.append("$");
			board.append(String.format("%,"+moneyLength+"d",Math.abs(playerMoney)));
			//Now the booster display
			switch(players.get(i).status)
			{
			case ALIVE:
			case DONE:
				board.append(" [");
				board.append(String.format("%3d",players.get(i).booster));
				board.append("%");
				if(players.get(i).status == PlayerStatus.DONE || (gameStatus == GameStatus.END_GAME && currentTurn == i))
				{
					board.append("x");
					board.append(players.get(i).winstreak);
				}
				board.append("]");
				break;
			case OUT:
			case FOLDED:
				board.append(" [OUT] ");
				break;
			}
			//If they have any games, print them too
			if(players.get(i).games.size() > 0)
			{
				board.append(" {");
				for(Games minigame : players.get(i).games)
				{
					board.append(" " + minigame.getShortName());
				}
				board.append(" }");
			}
			board.append("\n");
			//If we want the totals as well, do them on a second line
			if(totals)
			{
				//Get to the right spot in the line
				for(int j=0; j<(nameLength-4); j++)
					board.append(" ");
				board.append("Total:");
				//Print sign
				if(players.get(i).money<0)
					board.append("-");
				else
					board.append(" ");
				//Then print the money itself
				board.append("$");
				board.append(String.format("%,"+moneyLength+"d\n\n",Math.abs(players.get(i).money)));
			}
		}
		//Close it off and print it out
		board.append("```");
		channel.sendMessage(board.toString()).queue();
	}
	static void saveData()
	{
		try
		{
			List<String> list = Files.readAllLines(Paths.get("scores.csv"));
			//Replace the records of the players if they're there, otherwise add them
			for(int i=0; i<playersJoined; i++)
			{
				if(players.get(i).newbieProtection == 1)
					channel.sendMessage(players.get(i).user.getAsMention() + ", your newbie protection is now expired. "
							+ "From now on, bomb penalties will be $250,000.");
				int location = findUserInList(list,players.get(i).uID,false);
				String toPrint = players.get(i).uID+":"+players.get(i).name+":"+players.get(i).money
						+":"+players.get(i).booster+":"+players.get(i).winstreak
						+":"+(Math.max(players.get(i).newbieProtection-1,0));
				if(location == -1)
					list.add(toPrint);
				else
					list.set(location,toPrint);
			}
			//Then sort and rewrite it
			DescendingScoreSorter sorter = new DescendingScoreSorter();
			list.sort(sorter);
			Path file = Paths.get("scores.csv");
			Path fileOld = Paths.get("scoresOld.csv");
			Files.delete(fileOld);
			Files.copy(file,fileOld);
			Files.delete(file);
			Files.write(file, list);
		}
		catch(IOException e)
		{
			e.printStackTrace();
		}
	}
	public static int findUserInList(List<String> list, String userID, boolean searchByName)
	{
		int field;
		if(searchByName)
			field = 1;
		else
			field = 0;
		/*
		 * record format:
		 * record[0] = uID
		 * record[1] = name
		 * record[2] = money
		 * record[3] = booster
		 * record[4] = winstreak
		 */
		String[] record;
		for(int i=0; i<list.size(); i++)
		{
			record = list.get(i).split(":");
			if(record[field].compareToIgnoreCase(userID) == 0)
				return i;
		}
		return -1;
	}
	public static String listPlayers(boolean waitingOn)
	{
		StringBuilder resultString = new StringBuilder();
		if(waitingOn)
			resultString.append("**WAITING ON**");
		else
			resultString.append("**PLAYERS**");
		for(Player next : players)
		{
			if(!waitingOn || (waitingOn && next.status == PlayerStatus.OUT))
			{
				resultString.append(" | ");
				resultString.append(next.name);
			}
		}
		return resultString.toString();
	}
	public static void splitAndShare(int totalToShare)
	{
		channel.sendMessage("Because " + players.get(currentTurn).user.getAsMention() + " had a split and share, "
				+ "10% of their total will be split between the other players.").queueAfter(1,TimeUnit.SECONDS);
		for(int i=0; i<playersJoined; i++)
			//Don't pass money back to the player that hit it
			if(i != currentTurn)
			{
				//And divide the amount given by how many players there are to receive it
				players.get(i).addMoney(totalToShare / (playersJoined-1),MoneyMultipliersToUse.NOTHING);
			}
	}
}