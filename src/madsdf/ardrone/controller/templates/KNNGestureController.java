package madsdf.ardrone.controller.templates;

import bibliothek.gui.DockController;
import bibliothek.gui.DockStation;
import bibliothek.gui.Dockable;
import bibliothek.gui.dock.DefaultDockable;
import bibliothek.gui.dock.SplitDockStation;
import bibliothek.gui.dock.station.split.SplitDockGrid;
import static com.google.common.base.Preconditions.*;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JFrame;
import madsdf.ardrone.utils.DataFileReader;
import madsdf.ardrone.utils.DataFileReader.Gesture;
import madsdf.ardrone.utils.PropertiesReader;
import madsdf.ardrone.utils.WindowAccumulator;
import madsdf.shimmer.gui.AccelGyro;
import javax.swing.SwingUtilities;
import madsdf.ardrone.ARDrone;
import madsdf.ardrone.ActionCommand;
import madsdf.ardrone.controller.DroneController;

/**
 * Controller based on matching incoming measurements with gesture templates
 */
public class KNNGestureController extends DroneController {
    public static class GestureTemplate implements Comparable<GestureTemplate> {
        public final ActionCommand command;
        public final Gesture gesture;
        public GestureTemplate(ActionCommand cmd, Gesture g) {
            this.command = cmd;
            this.gesture = g;
        }

        @Override
        public int compareTo(GestureTemplate that) {
            return ComparisonChain.start()
                .compare(this.command, that.command)
                .result();
        }
    }
    
    private static final Pattern fnamePattern = Pattern.compile("(\\w+)_movement_\\d+_\\d+.txt");
    
    public static KNNGestureController FromProperties(
            String name,
            ImmutableSet<ActionCommand> actionMask, ARDrone drone,
            EventBus ebus, String configSensor) throws Exception {
        PropertiesReader reader = new PropertiesReader(configSensor);
        checkState(reader.getString("class_name").equals(KNNGestureController.class.getName()));
        
        String sensorDataBasedir = reader.getString("sensor_basedir");
        //String templates_file = sensorDataBasedir + "/" + reader.getString("templates_file");
        
        PropertiesReader descReader = new PropertiesReader(sensorDataBasedir + "/" + reader.getString("desc_file"));
        boolean calibrated = descReader.getBoolean("calibrated");
        // MovementsMap : convert from <String, String> to <Integer, String>
        Map<String, String> _movementsMap = descReader.getMap("movements_map");
        Map<Integer, ActionCommand> movementsMap = Maps.newHashMap();
        for (Entry<String, String> e : _movementsMap.entrySet()) {
            final ActionCommand a = ActionCommand.valueOf(e.getValue());
            movementsMap.put(Integer.parseInt(e.getKey()), a);
        }
        System.out.println(movementsMap);
        
        
        // Read all "SHIMID_movement_xx_xx.txt" files in directory
        File[] templateFiles = new File(sensorDataBasedir).listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.contains("movement") && name.endsWith(".txt");
            }
        });
        List<Gesture> gestures = Lists.newArrayList();
        for (File tf : templateFiles) {
            final Matcher m = fnamePattern.matcher(tf.getName());
            checkState(m.matches());
            final String shimmerID = m.group(1);
            
            final DataFileReader freader = new DataFileReader(new FileReader(tf));
            
            // Calibrated if needed
            if (calibrated) {
                List<Gesture> tmp = freader.readAll();
                for (Gesture g : tmp) {
                    gestures.add(g.calibrateGesture(shimmerID));
                }
            } else {
                gestures.addAll(freader.readAll());
            }
        }
        /*final DataFileReader freader = new DataFileReader(new FileReader(templates_file));
        List<Gesture> gestures = freader.readAll();*/
        List<GestureTemplate> templates = Lists.newArrayList(); 
        for (Gesture g : gestures) {
            final ActionCommand cmd = movementsMap.get(g.command);
            templates.add(new GestureTemplate(cmd, g));
        }
        
        
        final int windowSize = descReader.getInteger("windowsize");
        
        final String detectorName = KNNGestureController.class.getPackage().getName()
                + "." + descReader.getString("detector");
        GestureDetector detector = (GestureDetector)Class.forName(
                detectorName).newInstance();
         
        KNNGestureController ctrl = new KNNGestureController(name, actionMask,
                drone, templates, calibrated, windowSize, detector);
        ebus.register(ctrl);
        return ctrl;
    }
    
    private final Multimap<ActionCommand, GestureTemplate> gestureTemplates = ArrayListMultimap.create();
    private final WindowAccumulator accumulator;
    
    private TimeseriesChartPanel distChartPanel;
    private TimeseriesChartPanel stdChartPanel;
    private TimeseriesChartPanel knnChartPanel;
    private TimeseriesChartPanel detectedChartPanel;
    
    private JFrame chartFrame;
    private DockController dockController;
    
    private GestureDetector gestureDetector;
    
    public static final int KNN_K = 3;
    
    private final boolean calibrated;
    
    public KNNGestureController(final String name,
                                ImmutableSet<ActionCommand> actionMask,
                                ARDrone drone,
                                List<GestureTemplate> templates,
                                boolean calibrated,
                                int windowsize,
                                GestureDetector detector) {
        super(actionMask, drone);
        this.calibrated = calibrated;
        this.gestureDetector = detector;
        
        accumulator = new WindowAccumulator<>(windowsize, 15);
        
        for (GestureTemplate g: templates) {
            gestureTemplates.put(g.command, g);
        }
        
        System.out.println("-- DTW Gesture Controller, number of templates per command");
        for (ActionCommand command : gestureTemplates.keySet()) {
            System.out.println("command : " + command + " : " +
                    gestureTemplates.get(command).size());
        }
        
        // Create the user configuration frame
        java.awt.EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                // Dockable frame creation
                chartFrame = new JFrame();
                chartFrame.setTitle(name);
                dockController = new DockController();
                dockController.setRootWindow(chartFrame);
                chartFrame.addWindowListener(new WindowAdapter() {
                    @Override
                   public void windowClosing(WindowEvent e) {
                       dockController.kill();
                   } 
                });
                SplitDockStation station = new SplitDockStation();
                dockController.add(station);
                chartFrame.add(station);
                
                final int GRID_SIZE = 200;
                chartFrame.setBounds(40, 40, 2*GRID_SIZE, 2*GRID_SIZE);
                
                
                SplitDockGrid grid = new SplitDockGrid();
                
                // Chart panels
                ImmutableSortedMap.Builder<Integer, String> b = ImmutableSortedMap.naturalOrder();
                for (ActionCommand a : gestureTemplates.keySet()) {
                    b.put(a.ordinal(), a.name());
                }
                ImmutableSortedMap<Integer, String> commandIDToName = b.build();
                
                distChartPanel = new TimeseriesChartPanel(
                        "Distance to gesture templates",
                        "Windows", "DTW distance", commandIDToName);
                Dockable d = createDockable(distChartPanel);
                grid.addDockable(0, 0, GRID_SIZE, GRID_SIZE, d);
                
                knnChartPanel = new TimeseriesChartPanel(
                        "KNN votes",
                        "Windows", "votes", commandIDToName);
                d = createDockable(knnChartPanel);
                grid.addDockable(0, GRID_SIZE, GRID_SIZE, GRID_SIZE, d);
                stdChartPanel = new TimeseriesChartPanel(
                        "Standard deviation",
                        "Windows", "Stddev", ImmutableSortedMap.of(0, "Stddev"));
                d = createDockable(stdChartPanel);
                grid.addDockable(GRID_SIZE, 0, GRID_SIZE, GRID_SIZE, d);
                
                detectedChartPanel = new TimeseriesChartPanel(
                        "Detected gestures",
                        "Windows", "Detected", commandIDToName);
                d = createDockable(detectedChartPanel);
                grid.addDockable(GRID_SIZE, GRID_SIZE, GRID_SIZE, GRID_SIZE, d);
                
                station.dropTree(grid.toTree());
                
                chartFrame.setVisible(true);
                
            }
        });
    }
    
    private static Dockable createDockable(TimeseriesChartPanel chartPanel) {
        DefaultDockable dockable = new DefaultDockable();
        dockable.setTitleText(chartPanel.title);
        dockable.add(chartPanel);
        return dockable;
    }
    
    private static float[][] windowAccelToFloat(
            ArrayList<AccelGyro.Sample> window) {
        float[][] data = new float[3][window.size()];
        for (int i = 0; i < window.size(); ++i) {
            final AccelGyro.Sample sample = window.get(i);
            data[0][i] = sample.accel[0];
            data[1][i] = sample.accel[1];
            data[2][i] = sample.accel[2];
        }
        return data;
    }
    
    public float average(Collection<Float> col) {
        float sum = 0;
        for (Float f: col) {
            sum += f;
        }
        return sum / col.size();
    }
    
    public float stddev(float[] arr) {
        float avg = 0;
        for (int i = 0; i < arr.length; ++i) {
            avg += arr[i];
        }
        avg /= arr.length;
        
        float stddev = 0;
        for (int i = 0; i < arr.length; ++i) {
            final float v = arr[i] - avg;
            stddev += v*v;
        }
        return (float) Math.sqrt(stddev);
    }
    
    public ImmutableMap<Integer, Float> toIntegerMap(ImmutableMap<ActionCommand, Float> m) {
        ImmutableMap.Builder<Integer, Float> outM = ImmutableMap.builder();
        for (Entry<ActionCommand, Float> e : m.entrySet()) {
            outM.put(e.getKey().ordinal(), e.getValue());
        }
        return outM.build();
    }
    
    private void matchWindow(float[][] windowAccel) {
        KNN knn = KNN.classify(KNN_K, windowAccel, gestureTemplates);
        
        ImmutableMap.Builder<Integer, Float> cmdDists = ImmutableMap.builder();
        for (ActionCommand command: knn.distsPerClass.keySet()) {
            Collection<Float> dists = knn.distsPerClass.get(command);
            //final float dist = Collections.min(dists);
            final float dist = average(dists);
            cmdDists.put(command.ordinal(), dist);
        }
        updateChart(distChartPanel, cmdDists.build());
        
        //System.out.println(_tmp);
        updateChart(knnChartPanel, toIntegerMap(knn.votesPerClass));
        
        float meanStddev = (stddev(windowAccel[0]) + stddev(windowAccel[1])
                + stddev(windowAccel[2])) / 3.0f;
        ImmutableMap<Integer, Float> chartData = ImmutableMap.of(0, meanStddev);
        updateChart(stdChartPanel, chartData);
        
        // Finally, decide if we detected something
        decideGesture(knn, meanStddev);
    }
    
    private static void updateChart(final TimeseriesChartPanel panel,
                                    final ImmutableMap<Integer, Float> data) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                panel.addToChart(data);
            }
        });
    }
    
    
    private void decideGesture(final KNN knn,
                               float stddev) {
        gestureDetector.addVotation(knn, stddev);
        final ActionCommand detected = gestureDetector.decide();
        
        float duration;
        if (gestureDetector.hasActionDuration()) {
            duration = gestureDetector.getDurationMS();
        } else {
            duration = 1.0f;
        }
        ImmutableMap.Builder<ActionCommand, Float> _detections = ImmutableMap.builder();
        for (ActionCommand command: gestureTemplates.keySet()) {
            if (detected.equals(command)) {
                _detections.put(command, (float)duration);
            } else {
                _detections.put(command, 0.0f);
            }
        }
        ImmutableMap<ActionCommand, Float> detections = _detections.build();
        updateChart(detectedChartPanel, toIntegerMap(detections));
        if (gestureDetector.hasActionDuration()) {
            sendToDrone(detections);
        } else {
            for (Entry<ActionCommand, Float> e : detections.entrySet()) {
                this.directUpdateDroneAction(e.getKey(), e.getValue() > 0);
            }
        } 
    }
    
    private void sendToDrone(Map<ActionCommand, Float> detections) {
        for (Entry<ActionCommand, Float> e : detections.entrySet()) {
            final float duration = e.getValue();
            if (duration > 0) {
                System.out.println("enablingAction : " + e.getKey());
                this.enableAction(e.getKey(), (long)duration);
            }
        }
    }
    
    private void onSample(AccelGyro.Sample sample) {
        ArrayList<AccelGyro.Sample> window = accumulator.add(sample);
        if (window != null) {
            matchWindow(windowAccelToFloat(window));
        }
    }
    
    @Subscribe
    public void sampleReceived(AccelGyro.UncalibratedSample sample) {
        if (!calibrated) {
            onSample(sample);
        }
    }
    
    @Subscribe
    public void sampledReceived(AccelGyro.CalibratedSample sample) {
        if (calibrated) {
            onSample(sample);
        }
    }
}
