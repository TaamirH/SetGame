package bguspl.set.ex;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

import bguspl.set.Config;
import bguspl.set.Env;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    private final Dealer dealer;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    public Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;

    private boolean isFrozen;

    private long freezeStartTime;

    public ConcurrentLinkedQueue<Integer> actions;

    public int howmanytokens;
    public boolean wasSetLegal;
    public boolean PointOrFreeze;
    
    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.dealer=dealer;
        this.table = table;
        this.id = id;
        this.human = human;
        this.isFrozen=false;
        this.actions = new ConcurrentLinkedQueue<>();
        this.howmanytokens=0;
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.info("thread " + Thread.currentThread().getName() + " starting."+ "player id: "+id);
        if (!human) createArtificialIntelligence();

        while (!terminate ) {
            processQueue();
            
        }
        if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {}
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very, very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
            while (!terminate) {
                    int randomKeyPress = generateRandomKeyPress();
                    keyPressed(randomKeyPress);

                try {
                    synchronized (this) { Thread.sleep(30); }
                } catch (InterruptedException ignored) {}
            }
            env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }
    private int generateRandomKeyPress() {
        int howmany = table.countCards();
        if (howmany>=0)
            return new Random().nextInt(howmany+1);
        return 0;
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        terminate=true;
        try{
            if(playerThread!=null){
                env.logger.info("thread " + Thread.currentThread().getName() + " terminating.");
                playerThread.join();
            }
            if (aiThread!=null){
                env.logger.info("thread " + Thread.currentThread().getName() + " terminating.");
                aiThread.interrupt();
                aiThread.join();}
            }
        catch (InterruptedException e){
            Thread.currentThread().interrupt();
        };
        
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        if (howmanytokens==3 || slot<0 || slot>=table.countCards()){
            env.logger.warning( "Player " + id + " cannot perform any more actions.");
            return;
        }
        
        else if (isFrozen) {
            env.logger.warning( "Player " + id + " is frozen and cannot perform any action.");
            return;
        }
        else if (actionsIsFull()) {
            env.logger.warning( "Player " + id + " cannot perform any more actions.");
            return;
        }
        else{env.logger.warning( "Player " + id + " placed into actions in slot :"+ slot);

        actions.add(slot);}

    }
    private synchronized void processQueue(){
       
        //check if we need to add token or remove token
        if (!actions.isEmpty()&& ! terminate){
            boolean removed = false;
            int action = actions.poll();
            System.out.println("Player " + id + " action: " + action +"howmanytokens" + howmanytokens);
            for (int i=0;i<table.tokensPerPlayer.length;i++){
                if ((table.tokensPerPlayer[id][i]!=null)&&(action == table.tokensPerPlayer[id][i])){
                    removed =table.removeToken(id, action);
                    howmanytokens--;
                    break;
                }
            }
            if (!removed && howmanytokens<3){
                boolean placed = table.placeToken(id, action);
                howmanytokens++;
                if (howmanytokens==3 && placed){
                    dealer.WaitingForSet.add(id);
                    if (placed){
                        try{
                            synchronized(Thread.currentThread()){
                                Thread.currentThread().wait();}
                        }catch(InterruptedException e){
                            Thread.currentThread().interrupt();
                            if (wasSetLegal){
                                if (PointOrFreeze){
                                    point();}
                                else{
                                    penalty();}

                            }
                        wasSetLegal=false;
                        };
                    }
                }
            }
        }
    }
        
    
    

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        System.out.println("Player " + id + " scored a point.");
        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        score++;
        env.ui.setScore(id, score);
        env.ui.setFreeze(id,env.config.pointFreezeMillis);
        env.logger.warning( "Player " + id + " is frozen and cannot perform any action.");
        freezeStartTime=System.currentTimeMillis();
        isFrozen=true;
        while (System.currentTimeMillis() < freezeStartTime + env.config.pointFreezeMillis) {
            try{
                env.logger.warning( "Player " + id + " is frozen and cannot perform any action.");
                Thread.sleep(env.config.pointFreezeMillis);}
            catch(InterruptedException e){
                Thread.currentThread().interrupt();
            };

        }
        env.ui.setFreeze(id, 0);
        isFrozen=false;
    }

    public void penalty() {
        long penaltyDuration = env.config.penaltyFreezeMillis;
        env.ui.setFreeze(id, penaltyDuration);
        env.logger.warning("Player " + id + " is frozen and cannot perform any action.");
        freezeStartTime = System.currentTimeMillis();
        isFrozen = true;
    
        long lastDisplayedSecond = penaltyDuration / 1000;
    
        while (System.currentTimeMillis() < freezeStartTime + penaltyDuration) {
            long remainingTime = freezeStartTime + penaltyDuration - System.currentTimeMillis();
            long remainingSeconds = remainingTime / 1000;
    
            // Update UI only when the second changes
            if (remainingSeconds != lastDisplayedSecond) {
                env.ui.setFreeze(id, remainingTime);
                lastDisplayedSecond = remainingSeconds;
            }
    
            try {
                Thread.sleep(100); // Sleep for a short duration
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    
        isFrozen = false;
        env.ui.setFreeze(id, 0);
    }


    public int score() {
        return score;
    }

    public boolean addAction(int actionID) {
        // Check if capacity is reached
        if (actionsIsFull()) {
            return false; // Maximum capacity reached
        }

        return actions.add(actionID); // Add action and return true if successful
    }

    public boolean removeAction(int actionID) {
        return actions.remove(actionID); // Remove action and return true if successful
    }

    public List<Integer> getActions() {
        return new ArrayList<>(actions); // Return a copy of the actions
    }

    public boolean actionsIsFull() {
        return actions.size() == 3; // Check if maximum capacity is reached
    }
    
}
