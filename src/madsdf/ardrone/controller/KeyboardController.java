package madsdf.ardrone.controller;

import madsdf.ardrone.ActionCommand;
import madsdf.ardrone.ARDrone;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/**
 * The keyboard controller for control of the drone. This class implement a
 * KeyListener update the drone action map when an ActionCommand key is pressed
 * or released on the keyboard.
 *
 * Here is a description of the command :
 *
 * Takeoff : PageUp Landing : PageDown Hovering: Space (automatically hovering
 * when no command entry) Switch between video mode : V
 *
 * Arrow keys: Go Forward ^ | Go Left <---+---> Go Right | v Go Backward
 *
 * WASD keys: Go Up ^ W Rotate Left <-A-+-D-> Rotate Right S v Go Down
 *
 * Digital keys 1~9: Change speed (rudder rate 5%~99%), 1 is min and 9 is max.
 *
 * Java version : JDK 1.6.0_21 IDE : Netbeans 7.1.1
 *
 * @author Gregoire Aubert
 * @version 1.0
 */
public class KeyboardController extends DroneController implements KeyEventDispatcher {
    // Swing on Linux suffer from a bug where keyReleased is called right after
    // keyPressed even if the key is still pressed
    // Could implement timer workaround described there :
    // http://stackoverflow.com/questions/1736828/how-to-stop-repeated-keypressed-keyreleased-events-in-swing
    private static final int KEYBOARD_PRIORITY = 1;
    
    /**
     * Constructor
     *
     * @param arDrone the controlled drone
     */
    public KeyboardController(ImmutableSet<ActionCommand> actionMask,
                              ARDrone drone) {
        super(actionMask, drone);
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(this);
    }
    
    @Override
    public boolean dispatchKeyEvent(KeyEvent ke) {
        if (ke.getID() == KeyEvent.KEY_PRESSED) {
            this.drone.setSpeedMultiplier(1.0);
            final ActionCommand a = actionFromKeyCode(ke.getKeyCode());
            if (a != ActionCommand.NOTHING) {
                this.drone.setCommandPriority(KEYBOARD_PRIORITY);
                this.directUpdateDroneAction(a, true, KEYBOARD_PRIORITY);
                return true;
            }
        } else if (ke.getID() == KeyEvent.KEY_RELEASED) {
            this.directUpdateDroneAction(actionFromKeyCode(ke.getKeyCode()),
                    false, KEYBOARD_PRIORITY);
            this.drone.setCommandPriority(ARDrone.DEFAULT_PRIORITY);
        }
        return false;
    }
    /**
     * Convert a KeyEvent in an ActionCommand
     *
     * @param e the KeyEvent to convert
     * @return the corresponding ActionCommand
     */
    public static ActionCommand actionFromKeyCode(int keycode) {
        switch (keycode) {
            // Switch video channel
            case KeyEvent.VK_V:
                return ActionCommand.CHANGEVIDEO;

            // Go up (gaz+)
            case KeyEvent.VK_W:
                return ActionCommand.GOTOP;

            // Go down (gaz-)
            case KeyEvent.VK_S:
                return ActionCommand.GODOWN;

            // Rotate on the right (yaw+)
            case KeyEvent.VK_D:
                return ActionCommand.ROTATERIGHT;

            // Rotate on the left (yaw-)
            case KeyEvent.VK_A:
                return ActionCommand.ROTATELEFT;

            // Go forward (pitch+)
            case KeyEvent.VK_UP:
                return ActionCommand.GOFORWARD;

            // Go backward (pitch-)
            case KeyEvent.VK_DOWN:
                return ActionCommand.GOBACKWARD;

            // Move to the right (roll+)
            case KeyEvent.VK_RIGHT:
                return ActionCommand.GORIGHT;

            // Move to the left (roll-)
            case KeyEvent.VK_LEFT:
                return ActionCommand.GOLEFT;

            // Try to take off
            case KeyEvent.VK_PAGE_UP:
                return ActionCommand.TAKEOFF;

            // Land
            case KeyEvent.VK_PAGE_DOWN:
                return ActionCommand.LAND;

            // Force the drone to hover
            case KeyEvent.VK_SPACE:
                return ActionCommand.HOVER;

            default:
                return ActionCommand.NOTHING;
        }
    }
}
