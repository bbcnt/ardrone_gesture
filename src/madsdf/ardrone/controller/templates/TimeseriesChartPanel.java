/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package madsdf.ardrone.controller.templates;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Maps;
import info.monitorenter.gui.chart.Chart2D;
import info.monitorenter.gui.chart.IAxis;
import info.monitorenter.gui.chart.IAxis.AxisTitle;
import info.monitorenter.gui.chart.ITrace2D;
import info.monitorenter.gui.chart.traces.Trace2DLtd;
import java.awt.Color;
import java.awt.Paint;
import java.util.Map;
import javax.swing.SwingUtilities;
import madsdf.ardrone.ActionCommand;
import madsdf.shimmer.gui.ChartsDrawer;
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
public class TimeseriesChartPanel extends Chart2D {
    public final String title;
    
    private Map<Integer, ITrace2D> traces = Maps.newHashMap();
    
    // Return paints associated to serie id. This is used to ensure that
    // commands are plotted with the same color across the application
    private static Paint[] paints = ChartColor.createDefaultPaintArray();
    
    public TimeseriesChartPanel(String title,
                                String xAxisLabel, String yAxisLabel,
                                ImmutableSortedMap<Integer, String> seriesIDToName) {
        this(title, xAxisLabel, yAxisLabel, seriesIDToName, 100, 0, 1);
    }
    
    public TimeseriesChartPanel(String title,
                                String xAxisLabel, String yAxisLabel,
                                ImmutableSortedMap<Integer, String> seriesIDToName,
                                int numVisible,
                                float min, float max) {
        super();
        initComponents();
        
        this.title = title;
        
        for (Map.Entry<Integer, String> e : seriesIDToName.entrySet()) {
            ITrace2D trace = new Trace2DLtd(numVisible);
            trace.setName(e.getValue());
            trace.setColor(ChartsDrawer.colors[e.getKey()]);
            traces.put(e.getKey(), trace);
            this.addTrace(trace);
        }
        
        IAxis yAxis = this.getAxisY();
        yAxis.setAxisTitle(new AxisTitle(yAxisLabel));
        yAxis.setRangePolicy(new ChartsDrawer.RangePolicyMaxSeen(min, max));
        IAxis xAxis = this.getAxisX();
        xAxis.setAxisTitle(new AxisTitle(xAxisLabel));
    }
    
    // There are different way to update the chart. Note that you should
    // probably stick to ONE addToChart method. Mixing them might result in
    // undefined behaviour (due to the counter variable)
        
    // Add a set of values to the chart. One value for each serie
    public void addToChart(ImmutableMap<Integer, Float> data) {
        final long now = System.currentTimeMillis();
        for (Map.Entry<Integer, Float> e : data.entrySet()) {
            traces.get(e.getKey()).addPoint(now, e.getValue());
        }
        /*final long now = System.currentTimeMillis();
        final boolean notify = (now - lastAdd) > 200;
        if (notify) {
            lastAdd = now;
        }
        for (Map.Entry<Integer, Float> e : data.entrySet()) {
            //series.get(e.getKey()).addOrUpdate(new FixedMillisecond(counter), e.getValue());
            series.get(e.getKey()).add(new FixedMillisecond(counter), e.getValue(), notify);
        }
        counter++;*/
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
