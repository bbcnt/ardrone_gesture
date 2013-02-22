package madsdf.ardrone;

import madsdf.ardrone.controller.templates.KNNGestureController;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.*;
import java.util.Properties;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.swing.JFrame;
import javax.swing.JProgressBar;
import javax.swing.JTextArea;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.plaf.basic.BasicProgressBarUI;
import madsdf.shimmer.gui.ShimmerMoveAnalyzerFrame;
import static com.google.common.base.Preconditions.*;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Maps;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import madsdf.ardrone.controller.DroneController;
import madsdf.ardrone.controller.DummyController;
import madsdf.ardrone.controller.KeyboardController;
import madsdf.ardrone.controller.templates.TimeseriesChartPanel;
import madsdf.ardrone.utils.Utils;
import madsdf.shimmer.event.Globals;

/**
 * ########### Gesture Command of a Quadricopter ###########
 *
 * The goal of this project is to control an ARDrone Parrot with gesture
 * command. The gesture recognition is made with neural networks and two Shimmer
 * sensors each containing an accelerometer and a gyroscope. Here is the
 * principal class of the project. It manage the configuration of the drone and
 * create all the thread to communicate with it. This class manage the list of
 * command to send to the drone and maintain the action map. The action map is
 * updated by the controllers, which can be a keyboard controller or a neural
 * controller. The connection with the Shimmer sensors is entirely handled by
 * the neural controllers which depends on it.
 *
 * ########### Keyboard control mode ###########
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
public class ARDrone extends JFrame {

    private static final long serialVersionUID = 1L;

    static final String CONFIG_DRONE_PROPERTIES = "ardrone.properties";
    static final String CONFIG_MOVEMENT_MODEL = "gesture.SevenFeaturesSelectionMovement";
    // Drone ip adresse
    private InetAddress droneAdr;
    
    
    // Drone speed
    //private float speed = CONFIG_SPEED;
    // Speed multiplier. Can be used to modulate speed
    private float speedMultiplier = 1.0f;
    
    // Boolean action map
    private Map<ActionCommand, Boolean> commandState = Maps.newHashMap();
    
    private DroneClient droneClient;
    
    // The panel containing the video
    VideoPanel videoPanel;
    // The battery progress bar
    JProgressBar batteryLevel;
    JTextArea logArea;
    JButton resetButton;
    JButton reconnectButton;
    JSlider forwardBiasSlider;
    JSlider leftBiasSlider;
    JSlider speedSlider;
    TimeseriesChartPanel commandChart;
    TimeseriesChartPanel speedChart;
    TimeseriesChartPanel pcmdChart;
    // The properties objects
    private Properties configDrone;
    private final String leftShimmerID;
    private final String rightShimmerID;
    private ShimmerMoveAnalyzerFrame leftShimmer;
    private ShimmerMoveAnalyzerFrame rightShimmer;
    private final KeyboardController keyboardController;
    
    private final EventBus droneBus = new EventBus();
    private final EventBus controllerTickBus = new EventBus();
    
    private final Timer timer = new Timer();

    /**
     * Main process, create the drone controller.
     *
     * @param args the command line argument, can be the drone ip address
     */
    public static void main(String args[]) throws Exception {
        // Set the Default look and feel
        boolean nimbus = false;
        for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
            if ("Nimbus".equals(info.getName())) {
                javax.swing.UIManager.setLookAndFeel(info.getClassName());
                nimbus = true;
                break;
            }
        }
        if (!nimbus || System.getProperty("os.name").toLowerCase().indexOf("win") >= 0) {
            javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName());
        }

        // Create the drone controller
        final String rightShimmerID = "BDCD";
        final String leftShimmerID = "9EDB";
        ARDrone arDrone = new ARDrone(leftShimmerID, rightShimmerID);
    }

    /**
     * Constructor of the drone controller
     *
     * @param droneIp the ip address of the drone
     */
    public ARDrone(String leftShimmerID, String rightShimmerID) throws Exception {
        super();
        this.leftShimmerID = leftShimmerID;
        this.rightShimmerID = rightShimmerID;

        // Load the properties files
        loadProperties();
        droneClient = new DroneClient(droneBus);
        
        // Initialize commandState
        for (ActionCommand a : ActionCommand.values()) {
            commandState.put(a, false);
        }

        // Set the title and create the video panel
        this.setTitle("ARDrone gesture control");
        setIconImage(Toolkit.getDefaultToolkit().getImage(getClass().getResource("parrot_icon.png")));
        videoPanel = new VideoPanel();
        
        JPanel northPanel = new JPanel();
        northPanel.setLayout(new GridLayout(4, 2));
        
        this.getContentPane().add(northPanel, BorderLayout.NORTH);
        
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new GridLayout(1, 2));
        this.getContentPane().add(centerPanel, BorderLayout.CENTER);
        
        
        JPanel southPanel = new JPanel();
        southPanel.setLayout(new GridBagLayout());
        this.getContentPane().add(southPanel, BorderLayout.SOUTH);
        
        // Controls
        resetButton = new JButton("Reset");
        resetButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent ae) {
                droneClient.resetEmergency();
            }
            
        });
        
        reconnectButton = new JButton("Reconnect");
        reconnectButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent ae) {
                try {
                    droneClient.disconnect();
                    droneClient.connect(configDrone);
                } catch (IOException ex) {
                    Logger.getLogger(ARDrone.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        });
        
        northPanel.add(new JLabel("Controls"));
        JPanel controlsPanel = new JPanel();
        controlsPanel.add(resetButton);
        controlsPanel.add(reconnectButton);
        northPanel.add(controlsPanel);
        
        
        northPanel.add(new JLabel("Forward bias %"));
        forwardBiasSlider = new JSlider(JSlider.HORIZONTAL, -100, 100, 0);
        forwardBiasSlider.setMajorTickSpacing(20);
        forwardBiasSlider.setMinorTickSpacing(5);
        forwardBiasSlider.setPaintLabels(true);
        forwardBiasSlider.setPaintTicks(true);
        northPanel.add(forwardBiasSlider);
        
        northPanel.add(new JLabel("Left bias %"));
        leftBiasSlider = new JSlider(JSlider.HORIZONTAL, -100, 100, 0);
        leftBiasSlider.setMajorTickSpacing(20);
        leftBiasSlider.setMinorTickSpacing(5);
        leftBiasSlider.setPaintLabels(true);
        leftBiasSlider.setPaintTicks(true);
        northPanel.add(leftBiasSlider);
        
        northPanel.add(new JLabel("Speed %"));
        speedSlider = new JSlider(JSlider.HORIZONTAL, 0, 100, 20);
        speedSlider.setMajorTickSpacing(10);
        speedSlider.setMinorTickSpacing(5);
        speedSlider.setPaintLabels(true);
        speedSlider.setPaintTicks(true);
        northPanel.add(speedSlider);

        logArea = new JTextArea();
        logArea.setEnabled(false);
        centerPanel.add(logArea);
        
        commandChart = new TimeseriesChartPanel("Drone commands",
                "Time (samples)", "Activation", ActionCommand.ordinalToName,
                100, 0, 1);
        commandChart.setPreferredSize(new Dimension(0, 150));
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0;
        c.gridy = 0;
        c.weighty = 0.5;
        c.weightx = 1;
        southPanel.add(commandChart, c);
        
        speedChart = new TimeseriesChartPanel("Drone speed",
                "Time (samples)", "Speed", ImmutableSortedMap.of(0, "Speed"),
                100, 0, 1);
        speedChart.setPreferredSize(new Dimension(0, 150));
        c.gridy = 1;
        southPanel.add(speedChart, c);
        
        pcmdChart = new TimeseriesChartPanel("PCMD",
                "Time (samples)", "Value", ImmutableSortedMap.of(0, "flag",
                    1, "roll", 2, "pitch", 3, "gaz", 4, "yaw"),
                100, 0, 1);
        pcmdChart.setPreferredSize(new Dimension(0, 150));
        c.gridy = 2;
        southPanel.add(pcmdChart, c);
        
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                updateCharts();
            }
        }, 500, 100);

        // Discover the bluetooth devices if needed
      /*if(configSensors != null && configSensors.length > 0)
         BluetoothDiscovery.getInstance().launchDevicesDiscovery();*/


        // Define the frame
        videoPanel.setLayout(new FlowLayout(FlowLayout.RIGHT));
        centerPanel.add(videoPanel);

        // Define the battery progress bar
        batteryLevel = new JProgressBar(JProgressBar.HORIZONTAL, 0, 100);
        batteryLevel.setStringPainted(true);
        batteryLevel.setBackground(Color.WHITE);
        batteryLevel.setFocusable(false);
        batteryLevel.setRequestFocusEnabled(false);
        batteryLevel.setPreferredSize(new Dimension(52, 16));
        batteryLevel.setFont(new java.awt.Font("Tahoma", Font.BOLD, 11));
        batteryLevel.setUI(new BasicProgressBarUI() {
            @Override
            protected Color getSelectionBackground() {
                return Color.BLACK;
            }

            @Override
            protected Color getSelectionForeground() {
                return Color.BLACK;
            }
        });
        batteryLevel.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                if (batteryLevel.getValue() > 50) {
                    batteryLevel.setForeground(Color.GREEN);
                } else if (batteryLevel.getValue() < 15) {
                    batteryLevel.setForeground(Color.RED);
                } else {
                    batteryLevel.setForeground(Color.YELLOW);
                }
            }
        });
        videoPanel.add(batteryLevel);
        setSize(600, 800);
        setVisible(true);

        // Define the window closing action
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                System.exit(0);
            }
        });

        // Create the keyboard controller
        keyboardController = new KeyboardController(
                ActionCommand.allCommandMask(), this);
        
        //controlSender = new ControlSender(this, controllerTickBus);

        /*DummyController controller = new DummyController(ActionCommand.allCommandMask(), this);
        controllerTickBus.register(controller);*/
        
        leftShimmer = new ShimmerMoveAnalyzerFrame("Left", leftShimmerID);
        rightShimmer = new ShimmerMoveAnalyzerFrame("Right", rightShimmerID);
        leftShimmer.setVisible(true);
        rightShimmer.setVisible(true);

        EventBus leftBus = Globals.getBusForShimmer(leftShimmerID);
        EventBus rightBus = Globals.getBusForShimmer(rightShimmerID);

        // TODO: Should use configDrone.controller property
        //NeuralController.FromProperties(ActionCommand.allCommandMask(), this, rightBus, "mouvements_sensor_droit.properties");
        //NeuralController.FromProperties(ActionCommand.allCommandMask(), this, leftBus, "mouvements_sensor_gauche.properties");

        //ShimmerAngleController leftAngleController = ShimmerAngleController.FromProperties(ActionCommand.allCommandMask(), this, leftBus, "angle_left.properties");
        //ShimmerAngleController rightAngleController = ShimmerAngleController.FromProperties(ActionCommand.allCommandMask(), this, rightBus, "angle_right.properties");
        
        KNNGestureController rightGestureController =
                KNNGestureController.FromProperties("right",
                ActionCommand.allCommandMask(), this, rightBus,
                "dtw_gestures_right.properties");
        KNNGestureController leftGestureController =
                KNNGestureController.FromProperties("left",
                ActionCommand.allCommandMask(), this, leftBus,
                "dtw_gestures_left.properties");
        controllerTickBus.register(rightGestureController);
        controllerTickBus.register(leftGestureController);
        
        
        // Launch the configuration of the drone
        droneClient.connect(configDrone);
        
        droneBus.register(this);
        
        // Schedule commands sending task
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                sendCommands();
            }
        }, DroneClient.CMD_INTERVAL, DroneClient.CMD_INTERVAL);
        
        System.out.println("Running..");
    }
    
    private void updateCharts() {
        SwingUtilities.invokeLater(new Runnable(){
            @Override
            public void run() {
                // Commands map
                final ImmutableMap.Builder<Integer, Float> data = ImmutableMap.builder();
                for (Entry<ActionCommand, Boolean> e : commandState.entrySet()) {
                    final float v = e.getValue() ? 1 : 0;
                    data.put(e.getKey().ordinal(), v);
                }
                commandChart.addToChart(/*System.currentTimeMillis(),*/ data.build());
                
                // Speed
                speedChart.addToChart(ImmutableMap.of(0, getFinalSpeed()));
            }
        });
    }

    private void sendCommands() {
        // Let controllers think
        controllerTickBus.post(new DroneController.TickMessage());

        String status = "";
        // Verify if the drone is landing
        if (isActionLanding() || droneClient.getFlyingState() == FlyingState.LANDING) {
            if (droneClient.land()) {
                commandState.put(ActionCommand.LAND, false);
            }
        } else {
            // Verify if the drone is taking off
            if (isActionTakeOff() || droneClient.getFlyingState() == FlyingState.TAKING_OFF) {
                if (droneClient.takeOff()) {
                    commandState.put(ActionCommand.TAKEOFF, false);
                }
            }

            // If no movement key have been pressed, enter the hovering mode
            if (isActionHovering()
                    || !(isActionDown()
                    || isActionBackward()
                    || isActionForward()
                    || isActionLeft()
                    || isActionRight()
                    || isActionRotateLeft()
                    || isActionRotateRight()
                    || isActionTop())) {
                // Send the hovering command
                droneClient.sendPCMD(0, 0, 0, 0, 0);

                // Do the hovering ourselves and apply bias to fine-tune
                    /*roll = -myARDrone.getLeftBias();
                 pitch = -myARDrone.getForwardBias();
                 myARDrone.sendPCMD(1, roll, pitch, 0, 0);*/
            } else {
                // Define the flag for the movement commands mode
                float gaz, pitch, roll, yaw;
                roll = - getLeftBias()
                        + getFinalSpeed() * (+Utils.bToI(isActionRight())
                        - Utils.bToI(isActionLeft()));

                pitch = -getForwardBias()
                        + getFinalSpeed() * (Utils.bToI(isActionBackward())
                        - Utils.bToI(isActionForward()));

                gaz = getFinalSpeed() * (Utils.bToI(isActionTop())
                        - Utils.bToI(isActionDown()));

                yaw = getFinalSpeed() * (Utils.bToI(isActionRotateRight())
                        - Utils.bToI(isActionRotateLeft()));

                // Send the movement command
                droneClient.sendPCMD(1, roll, pitch, gaz, yaw);
            }
        }
    }
    
    /**
     * Load the properties of the drone and the sensors.
     */
    private void loadProperties() {
        // Create the drone properties object
        configDrone = new Properties();
        try {
            // Load the properties
            configDrone.load(new FileInputStream(CONFIG_DRONE_PROPERTIES));

            // Set the start speed
            String sp = configDrone.getProperty("speed", "");
            checkState(!sp.isEmpty());
            System.out.println("Ignoring speed property");
            //speed = Float.parseFloat(sp);

            // Verify if the sensors property is set
            String leftSensor = configDrone.getProperty("left_sensor");
            String rightSensor = configDrone.getProperty("right_sensor");
            checkState(!leftSensor.isEmpty());
            checkState(!rightSensor.isEmpty());
        } catch (FileNotFoundException ex) {
            System.err.println("ARDrone.loadProperties: " + ex);
        } catch (IOException ex) {
            System.err.println("ARDrone.loadProperties: " + ex);
        }
    }
    
    @Subscribe
    public void onVideoFrame(final VideoReader.VideoFrameEvent e) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                videoPanel.frameReceived(e.startX, e.startY, e.w, e.h, e.rgbArray,
                                         e.offset, e.scansize);
            }
        });
    }

    @Subscribe
    public void onNavData(final NavDataReader.NavDataEvent e) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                batteryLevel.setValue(e.battery);
            }
        });
    }
    
    /**
     * Update the map containing the action to send to the drone
     *
     * @param command the ActionCommand to update
     * @param startAction true to send the command and false to stop sending the
     * command
     */
    public synchronized void updateActionMap(ActionCommand command, boolean startAction) {
        // Add this single action to chart so we are sure the chart updating
        // thread doesn't miss it
        /*final float v = startAction ? 1 : 0;
        commandPanel.addToChart(System.currentTimeMillis(), command.ordinal(), v);*/
        updateCharts();
        
        // TODO: We should do the same with ControlSender (make sure it doesn't
        // miss too short commands)
         
        // process command
        switch (command) {
            case SPEED:
                switch (command.getVal()) {
                    case 1:
                        setSpeed(0.05, startAction);
                        break;
                    case 2:
                        setSpeed(0.1, startAction);
                        break;
                    case 3:
                        setSpeed(0.15, startAction);
                        break;
                    case 4:
                        setSpeed(0.25, startAction);
                        break;
                    case 5:
                        setSpeed(0.35, startAction);
                        break;
                    case 6:
                        setSpeed(0.45, startAction);
                        break;
                    case 7:
                        setSpeed(0.6, startAction);
                        break;
                    case 8:
                        setSpeed(0.8, startAction);
                        break;
                    case 9:
                        setSpeed(0.99, startAction);
                        break;
                }
                break;

            // Switch video channel
            case CHANGEVIDEO:
                if (startAction) {
                    droneClient.nextVideoChannel();
                }
                break;

            // Go up (gas+)
            case GOTOP:
            case GODOWN:
            case ROTATERIGHT:
            case ROTATELEFT:
            case GOFORWARD:
            case GOBACKWARD:
            case GORIGHT:
            case GOLEFT:
                commandState.put(command, startAction);
                break;
            // Try to take off
            case TAKEOFF:
                if (startAction) {
                    commandState.put(ActionCommand.TAKEOFF, true);
                    commandState.put(ActionCommand.LAND, false);
                    droneClient.takeOff();
                }
                break;

            // Land
            case LAND:
                if (startAction) {
                    commandState.put(ActionCommand.LAND, true);
                    commandState.put(ActionCommand.TAKEOFF, false);
                    droneClient.land();
                }
                break;

            // Force the drone to hover
            case HOVER:
//            System.out.println("Hovering");
                commandState.put(ActionCommand.HOVER, startAction);
                droneClient.sendPCMD(0, 0, 0, 0, 0);
                break;

            default:
                break;
        }
        updateLog();
    }

    private void updateLog() {
        String status = "";
        for (ActionCommand cmd : ActionCommand.values()) {
            status += cmd.name() + " : " + commandState.get(cmd) + "\n";
        }
        logArea.setText(status);
    }





    /**
     * @param speed the speed to set
     * @param set true to set the speed and false to ignore it
     */
    public void setSpeed(double speed, boolean set) {
        if (set) {
            speedSlider.setValue((int)(100*speed));
            //this.speed = (float) speed;

            // Print the speed
            System.out.println("Speed: " + getFinalSpeed());
        }
    }
    
    public void setSpeedMultiplier(double multiplier) {
        speedMultiplier = (float)multiplier;
    }
    
    public float getForwardBias() {
        return forwardBiasSlider.getValue() / 100.f;
    }
    
    public float getLeftBias() {
        return leftBiasSlider.getValue() / 100.f;
    }

    /**
     * @return the actual speed
     */
    public float getFinalSpeed() {
        return speedMultiplier * (speedSlider.getValue() / 100.f);
        //return speedMultiplier * speed;
    }

    /**
     * @return the take off action value
     */
    public boolean isActionTakeOff() {
        return commandState.get(ActionCommand.TAKEOFF);
    }

    /**
     * @return the landing action value
     */
    public boolean isActionLanding() {
        return commandState.get(ActionCommand.LAND);
    }

    /**
     * @return the top action value
     */
    public boolean isActionTop() {
        return commandState.get(ActionCommand.GOTOP);
    }

    /**
     * @return the down action value
     */
    public boolean isActionDown() {
        return commandState.get(ActionCommand.GODOWN);
    }

    /**
     * @return the forward action value
     */
    public boolean isActionForward() {
        return commandState.get(ActionCommand.GOFORWARD);
    }

    /**
     * @return the backward action value
     */
    public boolean isActionBackward() {
        return commandState.get(ActionCommand.GOBACKWARD);
    }

    /**
     * @return the left action value
     */
    public boolean isActionLeft() {
        return commandState.get(ActionCommand.GOLEFT);
    }

    /**
     * @return the right action value
     */
    public boolean isActionRight() {
         return commandState.get(ActionCommand.GORIGHT);
    }

    /**
     * @return the rotate left action value
     */
    public boolean isActionRotateLeft() {
         return commandState.get(ActionCommand.ROTATELEFT);
    }

    /**
     * @return the rotate right action value
     */
    public boolean isActionRotateRight() {
        return commandState.get(ActionCommand.ROTATERIGHT);
    }

    /**
     * @return the hovering action value
     */
    public boolean isActionHovering() {
         return commandState.get(ActionCommand.HOVER);
    }
}
