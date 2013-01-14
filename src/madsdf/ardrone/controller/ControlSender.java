package madsdf.ardrone.controller;

/**
 * The control sender is thread that read the drone action map at regular time
 * interval and generate the corresponding command to send to the drone.
 * 
 * Java version : JDK 1.6.0_21
 * IDE : Netbeans 7.1.1
 * 
 * @author Gregoire Aubert
 * @version 1.0
 */
public class ControlSender extends Thread {

   // The drone
   private ARDrone myARDrone;

   /**
    * Constructor
    * @param myARDrone the drone
    */
   public ControlSender(ARDrone myARDrone){
      this.myARDrone = myARDrone;
   }

   /**
    * Convert a boolean to an integer
    * @param b the boolean value to convert
    * @return the corresponding integer
    */
   private int bToI(boolean b){
      return (b)? 1: 0;
   }

   @Override
   public void run() {
      // Define the flag for the movement commands mode
      float gaz, pitch, roll, yaw;

      // Send commands each CONFIG_INTERVAL ms, to keep the connection alive
      while(!myARDrone.isExit()){
         String status = "";
         // Verify if the drone is landing
         if(myARDrone.isActionLanding() || myARDrone.getFlyingState() == FlyingState.LANDING){
            myARDrone.land();
         }
         else
         {
            // Verify if the drone is taking off
            if (myARDrone.isActionTakeOff() || myARDrone.getFlyingState() == FlyingState.TAKING_OFF) {
               myARDrone.takeOff();
            }

            // If no movement key have been pressed, enter the hovering mode
            if(myARDrone.isActionHovering()
                    || !(myARDrone.isActionDown()
                         || myARDrone.isActionBackward()
                         || myARDrone.isActionForward()
                         || myARDrone.isActionLeft()
                         || myARDrone.isActionRight()
                         || myARDrone.isActionRotateLeft()
                         || myARDrone.isActionRotateRight()
                         || myARDrone.isActionTop())){
               // Send the hovering command
               myARDrone.sendPCMD(0, 0, 0, 0, 0);
            } else {
               roll = myARDrone.getSpeed() * (+ bToI(myARDrone.isActionRight())
                                              - bToI(myARDrone.isActionLeft()));

               pitch = myARDrone.getSpeed() * (bToI(myARDrone.isActionBackward())
                                               - bToI(myARDrone.isActionForward()));

               gaz = myARDrone.getSpeed() * (bToI(myARDrone.isActionTop())
                                             - bToI(myARDrone.isActionDown()));

               yaw = myARDrone.getSpeed() * (bToI(myARDrone.isActionRotateRight())
                                             - bToI(myARDrone.isActionRotateLeft()));

               // Send the movement command
               myARDrone.sendPCMD(1, roll, pitch, gaz, yaw);
            }
         }

         // Sleep for CONFIG_INTERVAL ms
         try {
            Thread.sleep(ARDrone.CONFIG_INTERVAL);
         }
         catch (InterruptedException ex) {
            System.err.println("ControlSender.run: " + ex);
         }
      }
   }
}