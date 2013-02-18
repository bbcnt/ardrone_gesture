/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package madsdf.ardrone.controller.templates;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Maps;
import java.awt.Color;
import java.awt.Paint;
import java.util.Map;
import javax.swing.SwingUtilities;
import madsdf.ardrone.ActionCommand;
import org.jfree.chart.ChartColor;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.data.time.DynamicTimeSeriesCollection;
import org.jfree.data.time.FixedMillisecond;
import org.jfree.data.time.Second;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

/**
 *
 * @author julien
 */
public class TimeseriesChartPanel extends ChartPanel {
    private Map<Integer, TimeSeries> series = Maps.newHashMap();
    // Maps user-specified Integer id to Jfreechart timeseries collection id
    private Map<Integer, Integer> userIDToChartID = Maps.newHashMap();
    private JFreeChart chart;
    
    private int counter = Integer.MIN_VALUE;
    public final String title;
    
    private long lastAdd = System.currentTimeMillis();
    
    // Return paints associated to serie id. This is used to ensure that
    // commands are plotted with the same color across the application
    private static Paint[] paints = ChartColor.createDefaultPaintArray();
    
    public TimeseriesChartPanel(String title,
                                String xAxisLabel, String yAxisLabel,
                                ImmutableSortedMap<Integer, String> seriesIDToName) {
        this(title, xAxisLabel, yAxisLabel, seriesIDToName, 100);
    }
    
    public TimeseriesChartPanel(String title,
                                String xAxisLabel, String yAxisLabel,
                                ImmutableSortedMap<Integer, String> seriesIDToName,
                                int numVisible) {
        super(null);
        initComponents();
        
        this.title = title;
        
        TimeSeriesCollection accelCol = new TimeSeriesCollection();
        for (Map.Entry<Integer, String> e : seriesIDToName.entrySet()) {
            System.out.println(e.getValue() + " " + e.getKey());
            final TimeSeries s = new TimeSeries(e.getValue());
            series.put(e.getKey(), s);
            accelCol.addSeries(s);
            userIDToChartID.put(e.getKey(), accelCol.getSeriesCount() - 1);
        }
        
        chart = ChartFactory.createTimeSeriesChart(
                title,
                xAxisLabel,
                yAxisLabel,
                accelCol,
                true,
                false,
                false);
        
        XYPlot plot = chart.getXYPlot();
        plot.setRangeGridlinesVisible(false);
        plot.setDomainGridlinesVisible(false);
        plot.setBackgroundPaint(Color.WHITE);
        XYItemRenderer r = plot.getRenderer();
        
        for (Map.Entry<Integer, String> e : seriesIDToName.entrySet()) {
            final int v = e.getKey();
            r.setSeriesPaint(userIDToChartID.get(v), paints[v]);
        }
        
        ValueAxis timeAxis = plot.getDomainAxis();
        timeAxis.setTickMarksVisible(true);
        timeAxis.setMinorTickCount(10);
        timeAxis.setAutoRange(true);
        timeAxis.setFixedAutoRange(numVisible);
        timeAxis.setTickLabelsVisible(true);
        
        this.setChart(chart);
    }
    
    // There are different way to update the chart. Note that you should
    // probably stick to ONE addToChart method. Mixing them might result in
    // undefined behaviour (due to the counter variable)
        
    // Add a set of values to the chart. One value for each serie
    public void addToChart(ImmutableMap<Integer, Float> data) {
        final long now = System.currentTimeMillis();
        final boolean notify = (now - lastAdd) > 200;
        if (notify) {
            lastAdd = now;
        }
        for (Map.Entry<Integer, Float> e : data.entrySet()) {
            //series.get(e.getKey()).addOrUpdate(new FixedMillisecond(counter), e.getValue());
            series.get(e.getKey()).add(new FixedMillisecond(counter), e.getValue(), notify);
        }
        counter++;
    }
    
    // Add a set of values to the chart. One value for each serie
    /*public void addToChart(long timestampMS, ImmutableMap<Integer, Float> data) {
        for (Map.Entry<Integer, Float> e : data.entrySet()) {
            series.get(e.getKey()).addOrUpdate(new FixedMillisecond(timestampMS), e.getValue());
            //series.get(e.getKey()).add(new FixedMillisecond(timestampMS), e.getValue());
        }
    }*/
    
    // Add a unique value with a specific timestamp to the cart
    /*public void addToChart(long timestampMS, int serieID, Float value) {
        series.get(serieID).addOrUpdate(new FixedMillisecond(timestampMS), value);
    }*/

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 400, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 300, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents
    // Variables declaration - do not modify//GEN-BEGIN:variables
    // End of variables declaration//GEN-END:variables
}
