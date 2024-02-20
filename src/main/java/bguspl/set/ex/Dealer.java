package bguspl.set.ex;

import bguspl.set.Env;
import bguspl.set.ThreadLogger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.Iterator;


/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        ShuffleDeck();
        placeCardsOnTable();
        for (Player player : players) {
            Thread playerThread = new Thread(player);
            playerThread.start();
        }
        updateTimerDisplay(true);
        while (!shouldFinish()) {
            ShuffleDeck();
            placeCardsOnTable(); 
            updateTimerDisplay(true);
            timerLoop();
            updateTimerDisplay(false);
            removeAllCardsFromTable();
        }
        announceWinners();
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            ShuffleDeck();
            placeCardsOnTable();
        }
    }

    /**
     * Called when the game should be terminated.
     */
    public synchronized void terminate() {
        terminate=true;
        for (int i=0;i<players.length;i++){
            players[i].terminate();
        }
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable() {
        
    } 

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        synchronized (table) {
          
            // Retrieve card-to-slot mappings from the table
            Integer[] slotToCard = table.getSlotToCard();
            System.out.println(Arrays.toString(slotToCard));
    
            // Maintain a list of empty slots
            List<Integer> emptySlots = findEmptySlots(slotToCard);
            System.out.println(emptySlots);
    
            // Use an iterator to safely remove cards from the deck
            Iterator<Integer> deckIterator = deck.iterator();
    
            // Iterate through the deck and place cards in available empty slots
            while (deckIterator.hasNext() && !emptySlots.isEmpty()) {
                System.out.println("Empty slots: " + emptySlots.size() + " Deck size: " + deck.size());
                Integer card = deckIterator.next();
                int emptySlot = emptySlots.remove(0); // Get and remove the first empty slot
                System.out.println("Placing card " + card + " in slot " + emptySlot);
                table.placeCard(card, emptySlot);
                deckIterator.remove();
                System.out.println(emptySlots.isEmpty());

            }
           
        }
    }

        /**
         * Find an empty slot in the card-to-slot mappings.
         *
         * @param cardToSlot The array representing card-to-slot mappings.
         * @return The index of an empty slot or Table.NO_SLOT_AVAILABLE if no empty slot is found.
         */
    private List<Integer> findEmptySlots(Integer[] slotToCard) {
        List<Integer> emptySlots = new ArrayList<>();
            for (int i = 0; i < slotToCard.length; i++) {
                if (slotToCard[i] == null) {
                    emptySlots.add(i);
                }
            }
        System.out.println("Empty slots: " + emptySlots.size());
        return emptySlots;
}


    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private synchronized void sleepUntilWokenOrTimeout() {
        try {
            wait(950);
        }
        catch (InterruptedException e){
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        if (reset) {
            reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
            env.ui.setCountdown(env.config.turnTimeoutMillis, false);
        }
        else {
            long remainingTime = reshuffleTime - System.currentTimeMillis();
            env.ui.setCountdown(remainingTime, remainingTime < env.config.turnTimeoutWarningMillis);
        }
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        synchronized (table) { // Synchronize to ensure consistency
            // Retrieve card-to-slot mappings from the table
            Integer[] slotToCard = table.getSlotToCard();
            for (int i=0;i<table.tokensPerPlayer.length;i++){
                for (int j=0;j<table.tokensPerPlayer[i].length;j++){
                    if (table.tokensPerPlayer[i][j]!=null){
                        table.removeToken(i,table.tokensPerPlayer[i][j]);
                    }
                }
            }
    
            // Return the cards to the dealer's deck
            for (int slot = 0; slot < slotToCard.length; slot++) {
                Integer card = slotToCard[slot];
                if (card != null) {
                    table.removeCard(slot);
                    deck.add(card);
                }
            }
        }
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        int max = -1;
        LinkedList<Integer> winners = new LinkedList<Integer>();
        for (Player player:players){
            if (player.score()>max){
                max =player.score();
                winners.clear();
                winners.add(player.id);
            }
            else if (player.score()==max)
                winners.add(player.id);
        }
        if (!winners.isEmpty()) {
            System.out.print("The winner(s) is/are Player");
            for (int winnerId : winners) {
                System.out.print(" " + winnerId);
            }
            System.out.println();
        }
        //check if this is actually needed
        Integer[] aWinners = winners.toArray(new Integer[0]);
        int[] intWinners = Arrays.stream(aWinners).mapToInt(Integer::intValue).toArray();
        env.ui.announceWinner(intWinners); 
    }

    public boolean testSet(int[] cards,int id) { 
        System.out.println("Testing set for player " + id);
        for (int i=0;i<3;i++){
            System.out.println(cards[i]);
        }
        if( env.util.testSet(cards)){
            System.out.println("Player " + id + " has a set");
            players[id].point();
            updateTimerDisplay(true);
            //delete tokens of other people
             for (int i=0;i<cards.length;i++){
                if (table.getCardToSlot()[cards[i]]!=null){
                    int slot = table.getCardToSlot()[cards[i]];
                    for (int j=0;j<players.length;j++){
                        if (j!=id){
                            table.removeToken(j,slot);
                        }
                    }
                    table.removeCard(slot);}
                else    {
                    // the cards were changed so we need to clear the tokens
                    for (int j=0;j<players.length;j++){
                        if (j!=id){
                            for (int k=0;k<3;k++){
                                if (table.tokensPerPlayer[j][k]!=null && table.tokensPerPlayer[j][k]==cards[i]){
                                    table.removeToken(j,cards[i]);
                                }
                            }
                        }
                    }
                }}
            return true;
        }
        else{
            System.out.println("Player " + id + " does not have a set");
            players[id].penalty();
            return false;
    }
       
}
    public void ShuffleDeck(){
        synchronized(deck){
        for (int i=0;i<deck.size();i++){
            int randomIndex = (int) (Math.random() * deck.size());
            int temp = deck.get(i);
            deck.set(i,deck.get(randomIndex));
            deck.set(randomIndex,temp);
        }}
    }
}
