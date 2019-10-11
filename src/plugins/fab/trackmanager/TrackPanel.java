/*
 * Copyright 2010, 2011 Institut Pasteur.
 * 
 * This file is part of ICY.
 * 
 * ICY is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * ICY is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with ICY. If not, see <http://www.gnu.org/licenses/>.
 */
package plugins.fab.trackmanager;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;

import javax.swing.JPanel;
import javax.swing.event.ChangeEvent;

import icy.gui.main.GlobalSequenceListener;
import icy.gui.main.GlobalViewerListener;
import icy.gui.viewer.Viewer;
import icy.gui.viewer.ViewerEvent;
import icy.gui.viewer.ViewerListener;
import icy.main.Icy;
import icy.sequence.DimensionId;
import icy.sequence.Sequence;
import icy.system.thread.ThreadUtil;
import plugins.nchenouard.spot.Detection;

/**
 * Panel managing Tracks and users interactions.
 * 
 * @author Fabrice de Chaumont
 */
public class TrackPanel extends JPanel implements MouseListener, MouseMotionListener, TrackPainterListener,
        TrackManagerProcessorListener, ViewerListener, GlobalSequenceListener, GlobalViewerListener, Runnable
{

    /**
     * 
     */
    private static final long serialVersionUID = 938480382468773601L;

    TimeCursor timeCursor = new TimeCursor();

    /** Dimension of a single detection */
    private Dimension detectDim = new Dimension(10, 10);

    private TrackPool trackPool;

    public TrackPanel(TrackPool trackPool, boolean enableLeftPanelTrackDisplay)
    {

        super();
        this.enableLeftPanelTrackDisplay = enableLeftPanelTrackDisplay;
        this.trackPool = trackPool;
        trackPool.addTrackProcessorListener(this);
        this.addMouseListener(this);
        this.addMouseMotionListener(this);
        this.setBackground(Color.gray);
        trackPool.getTrackPainter().addTrackPainterListener(this);
        clearDetectionsAndReDisplay();

        setupViewerListener();

        Icy.getMainInterface().addGlobalSequenceListener(this);
        Icy.getMainInterface().addGlobalViewerListener(this);

        // System.out.println("TEST tackpanel");

    }

    void setupViewerListener()
    {
        // remove all listener

        for (Viewer viewer : Icy.getMainInterface().getViewers())
        {
            viewer.removeListener(this);
        }

        // listen to proper viewers

        Sequence sequence = trackPool.getDisplaySequence();
        if (sequence == null)
            return;

        for (Viewer viewer : Icy.getMainInterface().getViewers(sequence))
        {
            viewer.addListener(this);
        }
    }

    public void mouseClicked(MouseEvent e)
    {
        if (!enableLeftPanelTrackDisplay)
            return;

        MouseLink.clear();

        // Watch if a detection exists for selection.
        {
            TrackSegment ts = getTrackSegmentAt(e.getY());
            if (ts == null)
                return;
            ts.setAllDetectionSelected(!ts.isAllDetectionSelected());
            redrawDetection3();
        }

        // Feature: add the detection panel

        // // display edit Panel.
        // Detection d = ((TrackDetection) component).getDetection();
        // if (EventUtil.isControlDown(e))
        // d.setSelected(!d.isSelected());
        //
        // trackPool.getTrackEditor().deetPanel.removeAll();
        // trackPool.getTrackEditor().deetPanel.add(d.getDetectionEditor().getPanel());
        // d.getDetectionEditor().setDetection(d);
        // trackPool.getTrackEditor().deetPanel.updateUI();
        //

    }

    private TrackSegment getTrackSegmentAt(int y)
    {

        TrackSegment tsResult = null;

        y = y / getDetectDim().height;

        int currentLine = 0;

        loop: for (TrackGroup trackGroup : trackPool.getTrackGroupList())
            for (TrackSegment ts : trackGroup.getTrackSegmentList())
            {
                if (currentLine == y)
                {
                    tsResult = ts;
                    break loop;
                }
                currentLine++;
            }

        return tsResult;
    }

    private Detection getDetectionAt(int x, int y)
    {
        Detection detection = null;

        TrackSegment ts = getTrackSegmentAt(y);

        if (ts == null)
            return null;

        int t = x / getDetectDim().width;

        detection = ts.getDetectionAtTime(t);

        return detection;
    }

    public void mouseEntered(MouseEvent e)
    {

    }

    public void mouseExited(MouseEvent e)
    {

    }

    public void mousePressed(MouseEvent e)
    {
        if (!enableLeftPanelTrackDisplay)
            return;

        MouseLink.clear();

        MouseLink.xstart = e.getX();
        MouseLink.ystart = e.getY();

        MouseLink.xend = e.getX();
        MouseLink.yend = e.getY();

        // System.out.println( "debug info: mouse pressed.");

        Component component = getComponentAt(MouseLink.xstart, MouseLink.ystart);

        // System.out.println("Mouse pressed component: " + component );

        // if (component != null)
        // if (component instanceof TrackDetection)
        // {
        // System.out.println( "component test pass 1");
        //
        // MouseLink.xstart = (int) component.getBounds().getCenterX();
        // MouseLink.ystart = (int) component.getBounds().getCenterY();
        // MouseLink.TrackDetectionStart = (TrackDetection) component;
        // MouseLink.TrackDetectionStart.setSelected(true);
        // MouseLink.ispressed = true;
        // }

        // TrackSegment track = getTrackSegmentAt( e.getY() );
        // System.out.println("debug Track selected : " + track );

        Detection detection = getDetectionAt(e.getX(), e.getY());

        // System.out.println( "detection at click : " + detection );

        if (detection != null)
        {
            MouseLink.xstart = e.getX();
            MouseLink.ystart = e.getY();
            MouseLink.detectionStart = detection;
            MouseLink.ispressed = true;
            // System.out.println("Debug detection mouse link start : " + detection );
        }

        // System.out.println("debug Track selected : " + track );

        // manage track and time drag. Time has priority.
        if (component == this)
        {
            if (timeCursor.contains(e.getX(), e.getY()))
            {
                // enables time drag.
                timeCursor.dragging = true;
            }
            else
            {
                // look if at least one track exists.
                boolean oneAtLeast = false;
                for (TrackGroup trackGroup : trackPool.getTrackGroupList())
                {
                    if (trackGroup.getTrackSegmentList().size() != 0)
                    {
                        oneAtLeast = true;
                        break;
                    }
                }

                // enables trackDrag.
                // if ( trackPool.getTrackSegmentList().size() != 0 )
                if (oneAtLeast)
                {
                    TrackDrag.dragging = true;
                    TrackDrag.ts = getCurrentTrackSegmentWithY(e.getY());
                    repaint();
                }
            }
        }

    }

    /** Manage time cursor. */
    private class TimeCursor
    {
        int currentT = 0;
        boolean dragging = false;

        boolean contains(int x, int y)
        {
            Rectangle r = new Rectangle(currentT * (int) detectDim.getWidth(), 0, (int) detectDim.getWidth(),
                    Integer.MAX_VALUE);
            return r.contains(x, y);
        }
    }

    /**
     * Returns the track at Y //FIXME !
     */
    private TrackSegment getCurrentTrackSegmentWithY(int Y)
    {
        // System.out.println("drag a refaire.");
        // return null;
        TrackSegment ts = null;
        ts = trackPool.getTrackSegmentList().get(getCurrentTrackSegmentIndexWithY(Y));
        return ts;
    }

    /**
     * Returns the number of the track with Y //FIXME !
     */
    private int getCurrentTrackSegmentIndexWithY(int Y)
    {
        // System.out.println("drag a refaire.");
        // return 0;
        int index = (int) (Y / detectDim.getHeight());
        if (index < 0)
            index = 0;
        if (index >= trackPool.getTrackSegmentList().size())
            index = trackPool.getTrackSegmentList().size() - 1;
        return index;
    }

    /** class use to record Track Drag states. */
    private static class TrackDrag
    {

        static boolean dragging = false;
        static TrackSegment ts = null;
    }

    public void mouseReleased(MouseEvent e)
    {
        MouseLink.xend = e.getX();
        MouseLink.yend = e.getY();

        // System.out.println("mouse released");

        if (MouseLink.ispressed)
        {

            // System.out.println("Mouse link is pressed");

            Detection detection = getDetectionAt(e.getX(), e.getY());
            if (detection != null)
            {
                MouseLink.xend = e.getX();
                MouseLink.yend = e.getY();
                MouseLink.detectionEnd = detection;
                // System.out.println("Debug detection mouse link end : " + detection );
            }

            // Component component = getComponentAt(MouseLink.xend, MouseLink.yend);
            // if (component != null)
            // if (component instanceof TrackDetection)
            // {
            // MouseLink.xend = (int) component.getBounds().getCenterX();
            // MouseLink.yend = (int) component.getBounds().getCenterY();
            // MouseLink.TrackDetectionEnd = (TrackDetection) component;
            //
            // }

            // if (MouseLink.TrackDetectionStart != null)
            // {
            // MouseLink.TrackDetectionStart.setSelected(false);
            // }
            //
            // if (MouseLink.TrackDetectionEnd != null)
            // {
            // MouseLink.TrackDetectionEnd.setSelected(false);
            // }

            // System.out.println("mouse link is pressed, finishing mouse connexion");
            finishMouseConnexion();
            repaint();
        }
        MouseLink.clear();

        if (TrackDrag.dragging)
        {
            TrackDrag.dragging = false;
            repaint();
        }

        if (timeCursor.dragging)
        {
            timeCursor.dragging = false;
            repaint();
        }

    }

    public void mouseDragged(MouseEvent e)
    {
        if (MouseLink.ispressed)
        {
            MouseLink.xend = e.getX();
            MouseLink.yend = e.getY();

            // Component component = getComponentAt(MouseLink.xend, MouseLink.yend);
            //
            // // if no component are under the drag, check if a end component is active and deselect
            // // it.
            // if (component == this)
            // if (MouseLink.TrackDetectionEnd != null)
            // if (MouseLink.TrackDetectionEnd != MouseLink.TrackDetectionStart)
            // {
            // MouseLink.TrackDetectionEnd.setSelected(false);
            // MouseLink.TrackDetectionEnd = null;
            // }
            //
            // // Select the current component and restore state of an older selected component.
            // if (component != null)
            // if (component instanceof TrackDetection)
            // {
            // if (MouseLink.TrackDetectionEnd != (TrackDetection) component)
            // if (MouseLink.TrackDetectionEnd != null)
            // if (MouseLink.TrackDetectionEnd != MouseLink.TrackDetectionStart)
            // {
            // MouseLink.TrackDetectionEnd.setSelected(false);
            // MouseLink.TrackDetectionEnd = null;
            // }
            //
            // MouseLink.TrackDetectionEnd = (TrackDetection) component;
            // MouseLink.TrackDetectionEnd.setSelected(true);
            // }

            this.repaint();
        }

        // if track dragging , moves track.
        if (TrackDrag.dragging)
        {
            trackPool.moveTrackToIndex(TrackDrag.ts, getCurrentTrackSegmentIndexWithY(e.getY()));
            clearDetectionsAndReDisplay();
        }

        // if time dragging , moves times.
        if (timeCursor.dragging)
        {
            timeCursor.currentT = (int) (e.getX() / detectDim.getWidth());

            // System.out.println( TimeCursor.currentT );
            // System.out.println( "track editor : " + trackPool.getTrackEditor() );

            // FIXME : remettre ces securites.
            if (timeCursor.currentT < 0)
                timeCursor.currentT = 0;
            // if ( TimeCursor.currentT > trackPool.getSequence().getLength() -1 )
            // TimeCursor.currentT = trackPool.getSequence().getLength() - 1;
            // trackPool.getSequence().setSelectedT( TimeCursor.currentT );

            trackPool.getTrackManager().timeCursorChanged(timeCursor.currentT);
        }
    }

    public void mouseMoved(MouseEvent e)
    {

    }
    /*
     * @Override
     * protected void paintComponent(Graphics g)
     * {
     * super.paintComponent(g);
     * 
     * Graphics2D g2 = (Graphics2D) g;
     * 
     * // affichage des fond de tracks.
     * // boolean swapcolor = false;
     * // Color c1 = new Color( 0.8f ,0.8f , 0.8f );
     * // Color c2 = new Color( 0.9f ,0.9f , 0.9f );
     * //
     * 
     * // VERSION AVEC CLIPPER.
     * // int startTrackSegmentToDisplay = getVisibleRect().y / getDetectDim().height ;
     * // int lastTrackSegmentToDisplay = ( getVisibleRect().y+getVisibleRect().height ) /
     * // getDetectDim().height ;
     * // lastTrackSegmentToDisplay++;
     * //
     * // // test maxi depassé.
     * // if ( lastTrackSegmentToDisplay > trackPool.getTrackSegmentList().size() )
     * // lastTrackSegmentToDisplay = trackPool.getTrackSegmentList().size();
     * //
     * // for ( int tracknb = startTrackSegmentToDisplay ; tracknb < lastTrackSegmentToDisplay ;
     * // tracknb++ )
     * // for ( int tracknb = 0 ; tracknb < trackPool.getTrackSegmentList().size() ; tracknb++ )
     * 
     * // TODO : remettre la peinture au fond ( les bandes horizontales. )
     * // boolean switchBackground = false;
     * //
     * // for ( TrackGroup trackGroup : trackPool.getTrackGroupList() )
     * // for ( int tracknb = 0 ; tracknb < trackGroup.trackSegmentList.size() ; tracknb++ )
     * // {
     * // // display consecutive horizontal bars.
     * //
     * // switchBackground = !switchBackground;
     * // if ( switchBackground )
     * // {
     * // g2.setColor( c1 );
     * // }else
     * // {
     * // g2.setColor( c2 );
     * // }
     * //
     * // g2.fillRect(0, detectDim.height*tracknb , getWidth() , detectDim.height );
     * //
     * // // affichage des titres des tracks.
     * // g2.setColor( Color.black );
     * // g2.setFont( new Font( "Arial", Font.BOLD , detectDim.height - 2 ) );
     * // g2.drawString("#"+tracknb , 10 , detectDim.height*tracknb -2 );
     * //
     * // }
     * 
     * // draw current Time Cursor.
     * {
     * g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, .5f));
     * g2.setColor(Color.red);
     * g2.fillRect(getDetectDim().width * timeCursor.currentT, getVisibleRect().y, getDetectDim().width,
     * getVisibleRect().height);
     * g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
     * }
     * 
     * // draw current MouseLink user operation.
     * // FIXME : re-introduce mouse link user operations
     * // if (MouseLink.ispressed)
     * // {
     * // g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
     * // g2.setStroke(new BasicStroke(2));
     * // g2.setColor(Color.red);
     * // g2.drawLine(MouseLink.xstart, MouseLink.ystart, MouseLink.xend, MouseLink.yend);
     * // g2.setStroke(new BasicStroke(1));
     * // }
     * 
     * // emphasis track selection.
     * // if ( TrackDrag.dragging )
     * // {
     * // g2.setComposite( AlphaComposite.getInstance( AlphaComposite.SRC_OVER, .5f) );
     * // g2.setColor( Color.black );
     * // g2.fillRect( 0 , getDetectDim().height* trackPool.getTrackIndex( TrackDrag.ts ) ,
     * // getWidth() , getDetectDim().height );
     * // g2.setComposite( AlphaComposite.getInstance( AlphaComposite.SRC_OVER, 1.0f) );
     * // }
     * 
     * // TODO : affiche les liens entre tracks.
     * 
     * // {
     * // // Graphics2D g2 = (Graphics2D) this.getGraphics();
     * // // if ( g2 !=null )
     * // {
     * // g2.setColor( Color.green );
     * // g2.setStroke( new BasicStroke( 2 ) );
     * //
     * // for ( TrackGroup trackGroup : trackPool.getTrackGroupList() )
     * // for ( TrackSegment ts : trackGroup.trackSegmentList )
     * // //for ( int trackSegmentIndex = 0 ; trackSegmentIndex < TrackGroup ; trackSegmentIndex ++
     * // )
     * // //for ( int trackSegmentIndex = startTrackSegmentToDisplay ; trackSegmentIndex <
     * // lastTrackSegmentToDisplay ; trackSegmentIndex ++ )
     * // {
     * // //TrackSegment ts = trackPool.getTrackSegmentList().get( trackSegmentIndex );
     * //
     * // for ( TrackSegment ts2 : ts.nextList )
     * // {
     * //
     * //
     * // Detection detectionStart = ts.getLastDetection();
     * // Detection detectionEnd = ts2.getFirstDetection();
     * //
     * // // Detection detectionStart = ((Link)links.get(i)).getStartDetection();
     * // // Detection detectionEnd = ((Link)links.get(i)).getEndDetection();
     * //
     * // int xstart = getDetectDim().width * detectionStart.getT();
     * // int xend = getDetectDim().width * detectionEnd.getT();
     * //
     * // TrackSegment segmentStart = trackPool.getTrackSegmentWithDetection( detectionStart );
     * // TrackSegment segmentEnd = trackPool.getTrackSegmentWithDetection( detectionEnd );
     * //
     * // int ystart = getDetectDim().height * trackPool.getTrackIndex( segmentStart );
     * // int yend = getDetectDim().height * trackPool.getTrackIndex( segmentEnd );
     * //
     * // //center coords
     * // xstart+= getDetectDim().width/2;
     * // xend+= getDetectDim().width/2;
     * // ystart+= getDetectDim().height/2;
     * // yend+= getDetectDim().height/2;
     * //
     * // // boolean draw = false;
     * // //// if ( getVisibleRect().contains( xstart , ystart ) ) draw = true;
     * // //// if ( getVisibleRect().contains( xend , yend ) ) draw = true;
     * //
     * // // if ( draw )
     * // {
     * // //g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
     * // RenderingHints.VALUE_ANTIALIAS_ON);
     * // g2.drawLine( xstart, ystart, xend, yend );
     * //
     * // }
     * //
     * // }
     * // }
     * // g2.setStroke( new BasicStroke( 1 ) );
     * // }
     * // }
     * 
     * // if ( true ) return; // FIXME a enlever
     * 
     * // draw links.
     * // {
     * // ArrayList<Link> links = trackPool.getLinks();
     * // for ( int i = 0 ; i < links.size() ; i++ )
     * // {
     * // // startTrackSegmentToDisplay ;
     * // // lastTrackSegmentToDisplay ;
     * //
     * //
     * //
     * // int startTrackIndex = trackPool.getTrackSegmentList().indexOf(
     * // ((Link)links.get(i)).getStartSegment() );
     * // int endTrackIndex = trackPool.getTrackSegmentList().indexOf(
     * // ((Link)links.get(i)).getEndSegment() );
     * //
     * // boolean startIn = false;
     * // boolean endIn = false;
     * //
     * // startIn = startTrackIndex > startTrackSegmentToDisplay && startTrackIndex <
     * // lastTrackSegmentToDisplay ;
     * // endIn = endTrackIndex > startTrackSegmentToDisplay && endTrackIndex <
     * // lastTrackSegmentToDisplay ;
     * //
     * // if ( ! (startIn || endIn ) ) continue;
     * //
     * // Detection detectionStart = ((Link)links.get(i)).getStartDetection();
     * // Detection detectionEnd = ((Link)links.get(i)).getEndDetection();
     * //
     * // int xstart = getDetectDim().width * detectionStart.getT();
     * // int xend = getDetectDim().width * detectionEnd.getT();
     * //
     * // TrackSegment segmentStart = trackPool.getTrackSegmentWithDetection( detectionStart );
     * // TrackSegment segmentEnd = trackPool.getTrackSegmentWithDetection( detectionEnd );
     * //
     * // int ystart = getDetectDim().height * trackPool.getTrackIndex( segmentStart );
     * // int yend = getDetectDim().height * trackPool.getTrackIndex( segmentEnd );
     * //
     * // //center coords
     * // xstart+= getDetectDim().width/2;
     * // xend+= getDetectDim().width/2;
     * // ystart+= getDetectDim().height/2;
     * // yend+= getDetectDim().height/2;
     * //
     * // // boolean draw = false;
     * // //// if ( getVisibleRect().contains( xstart , ystart ) ) draw = true;
     * // //// if ( getVisibleRect().contains( xend , yend ) ) draw = true;
     * // //
     * // // if ( draw )
     * // {
     * // g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
     * // g2.setColor( Color.green );
     * // g2.setStroke( new BasicStroke( 2 ) );
     * // g2.drawLine( xstart, ystart, xend, yend );
     * // g2.setStroke( new BasicStroke( 1 ) );
     * // }
     * //
     * // }
     * // }
     * 
     * }
     */

    public Dimension getDetectDim()
    {
        return detectDim;
    }

    public void setDetectDim(Dimension detectDim)
    {
        this.detectDim = detectDim;
    }

    public void setTrackPool(TrackPool trackPool)
    {
        this.trackPool = trackPool;
        clearDetectionsAndReDisplay();

    }

    /**
     * Clear Detections and redisplay them.
     */
    private void clearDetectionsAndReDisplay()
    {
        // do it in background
        ThreadUtil.bgRunSingle(this);
    }

    /**
     * Recompute all processors
     */
    public void recomputeProcessors()
    {
        // important to do it on EDT (otherwise we can get death lock)
        ThreadUtil.invokeNow(new Runnable()
        {
            @Override
            public void run()
            {
                // reset color of detection
                for (Detection d : trackPool.getAllDetection())
                    d.reset();
                // then execute processors (important to do the whole in EDT so painting always happen after all processes)
                trackPool.computeTrackProcessor();
            }
        });
    }

    @Override
    public void run()
    {
        // refresh processors
        recomputeProcessors();
        // redraw left panel
        redrawDetection3();

        // update painters
        Sequence sequence = trackPool.getDisplaySequence();
        if (sequence != null)
            sequence.painterChanged(null);
    }

    // boolean disableLeftPanel= false;
    /**
     * Re-create the tracking image.
     */
    public void redrawDetection3()
    {
        if (!enableLeftPanelTrackDisplay)
            return;

        // if ( disableLeftPanel == true ) return;

        int maxSizeT = trackPool.getLastDetectionTimePoint();
        int totalNumberOfTrack = trackPool.getTotalNumberOfTrack();

        // check if size is too big

        // int maxSize = 1000000000; // should be 2 000 000 000
        int maxSize = 100000000; // should be 2 000 000 000
        int width = getDetectDim().width * (maxSizeT + 1);
        int height = getDetectDim().height * totalNumberOfTrack;

        if (width * height > maxSize)
        {
            height = maxSize / width;
            System.out.println(
                    "too much track to display them all in trackManager left panel. Still, they all appear on the sequence.");
        }

        // if ( trackingPanelSize.width * trackingPanelSize.height > maxSize )
        // {
        // trackingPanelSize.height = maxSize / trackingPanelSize.width ;
        // }

        Dimension trackingPanelSize = new Dimension(width, height);
        this.setPreferredSize(trackingPanelSize);
        revalidate();

        // drop image if dimensions have changed.
        if (bufferedImage != null)
            if ((bufferedImage.getWidth() != trackingPanelSize.width)
                    || (bufferedImage.getHeight() != trackingPanelSize.height))
            {
                bufferedImage = null;
            }

        // create cache image if it does not exists.
        if (bufferedImage == null)
        {
            if (!(trackingPanelSize.width == 0 || trackingPanelSize.height == 0))
            {
                try
                {
                    bufferedImage = new BufferedImage(trackingPanelSize.width, trackingPanelSize.height,
                            BufferedImage.TYPE_INT_RGB);
                }
                catch (OutOfMemoryError e)
                {
                    System.out.println("can't create " + trackingPanelSize.width + "x" + trackingPanelSize.height
                            + " bitmap for track representation in trackPanel");
                    System.out.println("left panel disabled");
                    enableLeftPanelTrackDisplay = false;
                    return;
                }
            }
            else
            {
                // no track to display.
                return;
            }
        }

        Graphics2D g = bufferedImage.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // fill background

        {
            Color c1 = new Color(0.8f, 0.8f, 0.8f);
            Color c2 = new Color(0.9f, 0.9f, 0.9f);
            int offsetY = 0;
            boolean switchBackground = true;
            g.setFont(new Font("Arial", Font.BOLD, detectDim.height - 2));
            for (TrackGroup trackGroup : trackPool.getTrackGroupList())
                for (int tracknb = 0; tracknb < trackGroup.getTrackSegmentList().size(); tracknb++)
                {
                    // display consecutive horizontal bars.

                    switchBackground = !switchBackground;
                    if (switchBackground)
                    {
                        g.setColor(c1);
                    }
                    else
                    {
                        g.setColor(c2);
                    }

                    g.fillRect(0, detectDim.height * offsetY, detectDim.width * (maxSizeT + 1), detectDim.height);
                    offsetY++;

                }
        }

        // draw detections

        int currentLine = 0; // current track line ( y offset )

        HashMap<Detection, Point2D> detectionHashMap = new HashMap<Detection, Point2D>();

        for (TrackGroup trackGroup : trackPool.getTrackGroupList())
            for (TrackSegment ts : trackGroup.getTrackSegmentList())
            // for ( int trackSegmentIndex = 0 ; trackSegmentIndex <
            // trackPool.getTrackSegmentList().size() ; trackSegmentIndex ++ )
            // for ( int trackSegmentIndex = startTrackSegmentToDisplay ; trackSegmentIndex <
            // lastTrackSegmentToDisplay ; trackSegmentIndex ++ )
            {
                // System.out.println("tracksegment... dsf " + ligne + " " +
                // ts.detectionList.size() );
                // TrackSegment ts = trackPool.getTrackSegmentList().get( trackSegmentIndex );

                for (int detectionIndex = 0; detectionIndex < ts.getDetectionList().size(); detectionIndex++)
                {
                    final Detection d = ts.getDetectionList().get(detectionIndex);

                    // TrackDetection td = new TrackDetection(d);

                    // specification de la taille des panels representant les detections ici.
                    // td.setBounds(0 + getDetectDim().width * d.getT(), 0 + getDetectDim().height * ligne, -0
                    // + getDetectDim().width, -0 + getDetectDim().height);
                    // add(td);

                    Rectangle2D bounds = new Rectangle2D.Double(0 + getDetectDim().width * d.getT(),
                            0 + getDetectDim().height * currentLine, -0 + getDetectDim().width,
                            -0 + getDetectDim().height);
                    drawDetection(g, d, bounds);

                    detectionHashMap.put(d,
                            new Point2D.Float(getDetectDim().width * d.getT(), getDetectDim().height * currentLine));

                }
                currentLine++;
            }

        // draw links between tracks

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(Color.red);
        g2.setStroke(new BasicStroke(3));

        ArrayList<Link> links = trackPool.getLinks();
        int detectHeight = getDetectDim().height / 2;
        int detectWidth = getDetectDim().width / 2;

        for (Link link : links)
        {
            Detection startDetection = link.getStartDetection();
            Detection endDetection = link.getEndDetection();

            Point2D startDetectionPoint2D = detectionHashMap.get(startDetection);
            Point2D endDetectionPoint2D = detectionHashMap.get(endDetection);

            if (startDetectionPoint2D != null && endDetectionPoint2D != null)
            {
                g2.drawLine((int) startDetectionPoint2D.getX() + detectHeight,
                        (int) startDetectionPoint2D.getY() + detectWidth,
                        (int) endDetectionPoint2D.getX() + detectHeight,
                        (int) endDetectionPoint2D.getY() + detectWidth);
            }
        }

        /*
         * 
         * // previous version
         * for (TrackGroup trackGroup : trackPool.getTrackGroupList())
         * for (TrackSegment ts : trackGroup.getTrackSegmentList())
         * {
         * Detection lastDetection = ts.getLastDetection();
         * if ( lastDetection !=null )
         * {
         * Point2D lastDetectionPoint2D = detectionHashMap.get( lastDetection );
         * 
         * for ( TrackSegment tsNext : ts.nextList )
         * {
         * Detection nextDetection = tsNext.getFirstDetection();
         * if ( nextDetection != null )
         * {
         * Point2D nextDetectionPoint2D = detectionHashMap.get( nextDetection );
         * if ( lastDetectionPoint2D !=null && nextDetectionPoint2D != null )
         * {
         * g2.drawLine(
         * (int)lastDetectionPoint2D.getX() + getDetectDim().height/2 ,
         * (int)lastDetectionPoint2D.getY() + getDetectDim().width/2 ,
         * (int)nextDetectionPoint2D.getX() + getDetectDim().height/2 ,
         * (int)nextDetectionPoint2D.getY() + getDetectDim().width/2 );
         * }
         * else
         * {
         * System.out.println("display error");
         * }
         * }
         * }
         * }
         * 
         * }
         */

        // display track number
        {
            int offsetY = 1;
            g.setColor(Color.white);
            for (TrackGroup trackGroup : trackPool.getTrackGroupList())
            {
                for (int tracknb = 0; tracknb < trackGroup.getTrackSegmentList().size(); tracknb++)
                {
                    g.drawString(trackGroup.getDescription() + " # " + tracknb, 10, detectDim.height * offsetY - 2);
                    offsetY++;
                }
            }
        }

        repaint();
    }

    private void drawDetection(Graphics2D g, Detection d, Rectangle2D bounds)
    {

        g.setColor(d.getColor());
        g.fill(bounds);
        g.setColor(d.getColor().darker());
        g.draw(bounds);

        if (d.isSelected())
        {
            g.setStroke(new BasicStroke(3));
            g.setColor(Color.white);
            g.draw(bounds);
            g.setStroke(new BasicStroke(1));
        }

        if (d.getDetectionType() == Detection.DETECTIONTYPE_VIRTUAL_DETECTION)
        {
            g.setStroke(new BasicStroke(2));
            g.setColor(Color.white);
            g.drawLine((int) bounds.getMinX(), (int) bounds.getMinY(), (int) bounds.getMaxX(), (int) bounds.getMaxY());
            g.setStroke(new BasicStroke(1));
        }

    }

    BufferedImage bufferedImage;

    boolean enableLeftPanelTrackDisplay = true;

    /**
     * Paint the tracking Panel
     */
    @Override
    protected void paintComponent(Graphics g1)
    {
        super.paintComponent(g1);
        Graphics2D g = (Graphics2D) g1;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (!enableLeftPanelTrackDisplay)
        {
            g.clearRect(0, 0, this.getWidth(), this.getHeight());
            g.drawString("Display disabled. Enable selecting Display > Enable Track Panel", 20, 20);
            return;
        }

        int totalNumberOfTrack = trackPool.getTotalNumberOfTrack();
        if (totalNumberOfTrack == 0)
        {
            g.drawString("No track to display", 20, 20);
        }

        if (bufferedImage == null)
            return;

        g.drawImage(bufferedImage, null, 0, 0);

        // draw MouseLink
        if (MouseLink.ispressed)
        {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setStroke(new BasicStroke(2));
            g.setColor(Color.red);
            g.drawLine(MouseLink.xstart, MouseLink.ystart, MouseLink.xend, MouseLink.yend);
            g.setStroke(new BasicStroke(1));
        }

        // draw current Time Cursor.
        {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, .5f));
            g.setColor(Color.red);
            g.fillRect(getDetectDim().width * timeCursor.currentT, getVisibleRect().y, getDetectDim().width,
                    getVisibleRect().height);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
        }

    }

    /**
     * @deprecated replaced by redrawDetection3()
     */
    public void redrawDetection2()
    {

        // FIXME: dimension should update ( le 400 est fixe ici... )
        // old code:
        // this.setPreferredSize( new Dimension (
        // getDetectDim().width * trackPool.getSequence().getLength() ,
        // getDetectDim().height * trackPool.getTrackSegmentList().size() ) );

        // System.out.println("passe dans redrawDetection2");

        this.setPreferredSize(new Dimension(getDetectDim().width * 400, getDetectDim().height * 400));

        this.removeAll();

        // int startTrackSegmentToDisplay = getVisibleRect().y / getDetectDim().height ;
        // int lastTrackSegmentToDisplay = ( getVisibleRect().y+getVisibleRect().height ) /
        // getDetectDim().height ;
        // lastTrackSegmentToDisplay++;
        //
        // // test maxi depasse
        // if ( lastTrackSegmentToDisplay > trackPool.getTrackSegmentList().size() )
        // lastTrackSegmentToDisplay = trackPool.getTrackSegmentList().size();
        //
        // System.out.println( "taille trackgrouplist: " + trackPool.getTrackGroupList().size() );

        int ligne = 0;
        for (TrackGroup trackGroup : trackPool.getTrackGroupList())
            for (TrackSegment ts : trackGroup.getTrackSegmentList())
            // for ( int trackSegmentIndex = 0 ; trackSegmentIndex <
            // trackPool.getTrackSegmentList().size() ; trackSegmentIndex ++ )
            // for ( int trackSegmentIndex = startTrackSegmentToDisplay ; trackSegmentIndex <
            // lastTrackSegmentToDisplay ; trackSegmentIndex ++ )
            {
                // System.out.println("tracksegment... dsf " + ligne + " " +
                // ts.detectionList.size() );
                // TrackSegment ts = trackPool.getTrackSegmentList().get( trackSegmentIndex );

                for (int detectionIndex = 0; detectionIndex < ts.getDetectionList().size(); detectionIndex++)
                {
                    final Detection d = ts.getDetectionList().get(detectionIndex);
                    // final TrackDetection td ;
                    // ThreadUtil.invokeLater( new Runnable() {
                    //
                    // @Override
                    // public void run() {
                    TrackDetection td = new TrackDetection(d);

                    // }
                    // });

                    // System.out.println( "detec : " + getDetectDim().height* ligne );

                    // specification de la taille des panels representant les detections ici.
                    td.setBounds(0 + getDetectDim().width * d.getT(), 0 + getDetectDim().height * ligne,
                            -0 + getDetectDim().width, -0 + getDetectDim().height);
                    add(td);
                }
                ligne++;
            }

        // {
        // Graphics2D g2 = (Graphics2D) this.getGraphics();
        // if ( g2 !=null )
        // {
        // g2.setColor( Color.green );
        // g2.setStroke( new BasicStroke( 2 ) );
        //
        // for ( int trackSegmentIndex = startTrackSegmentToDisplay ; trackSegmentIndex <
        // lastTrackSegmentToDisplay ; trackSegmentIndex ++ )
        // {
        // TrackSegment ts = trackPool.getTrackSegmentList().get( trackSegmentIndex );
        //
        // for ( TrackSegment ts2 : ts.nextList )
        // {
        //
        //
        // Detection detectionStart = ts.getLastDetection();
        // Detection detectionEnd = ts2.getFirstDetection();
        //
        // // Detection detectionStart = ((Link)links.get(i)).getStartDetection();
        // // Detection detectionEnd = ((Link)links.get(i)).getEndDetection();
        //
        // int xstart = getDetectDim().width * detectionStart.getT();
        // int xend = getDetectDim().width * detectionEnd.getT();
        //
        // TrackSegment segmentStart = trackPool.getTrackSegmentWithDetection( detectionStart );
        // TrackSegment segmentEnd = trackPool.getTrackSegmentWithDetection( detectionEnd );
        //
        // int ystart = getDetectDim().height * trackPool.getTrackIndex( segmentStart );
        // int yend = getDetectDim().height * trackPool.getTrackIndex( segmentEnd );
        //
        // //center coords
        // xstart+= getDetectDim().width/2;
        // xend+= getDetectDim().width/2;
        // ystart+= getDetectDim().height/2;
        // yend+= getDetectDim().height/2;
        //
        // // boolean draw = false;
        // //// if ( getVisibleRect().contains( xstart , ystart ) ) draw = true;
        // //// if ( getVisibleRect().contains( xend , yend ) ) draw = true;
        //
        // // if ( draw )
        // {
        // //g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
        // RenderingHints.VALUE_ANTIALIAS_ON);
        // g2.drawLine( xstart, ystart, xend, yend );
        //
        // }
        //
        // }
        // }
        // g2.setStroke( new BasicStroke( 1 ) );
        // }
        // }

        // ArrayList<Link> links = trackPool.getLinks();
        // for ( int i = 0 ; i < links.size() ; i++ )
        // {
        // // startTrackSegmentToDisplay ;
        // // lastTrackSegmentToDisplay ;
        //
        //
        //
        // int startTrackIndex = trackPool.getTrackSegmentList().indexOf(
        // ((Link)links.get(i)).getStartSegment() );
        // int endTrackIndex = trackPool.getTrackSegmentList().indexOf(
        // ((Link)links.get(i)).getEndSegment() );
        //
        // boolean startIn = false;
        // boolean endIn = false;
        //
        // startIn = startTrackIndex > startTrackSegmentToDisplay && startTrackIndex <
        // lastTrackSegmentToDisplay ;
        // endIn = endTrackIndex > startTrackSegmentToDisplay && endTrackIndex <
        // lastTrackSegmentToDisplay ;
        //
        // if ( ! (startIn || endIn ) ) continue;
        //

        //

        updateUI();

    }

    /** Should be called when a mouse-link operation has been finished */
    public void finishMouseConnexion()
    {
        // Look for particular case where the detection belongs to the same TrackSegment, and are
        // joined
        // --> cut tracksegment.

        // System.out.println("------ entering finishMouseConnexion");
        // System.out.println("MouseLink start : " + MouseLink.detectionStart );
        // System.out.println("MouseLink end : " + MouseLink.detectionEnd );

        if (MouseLink.detectionStart != null && MouseLink.detectionEnd != null)
        {
            // System.out.println("Enter detect start & end valid.");

            Detection d1 = MouseLink.detectionStart;
            Detection d2 = MouseLink.detectionEnd;
            if (d1.getT() + 1 == d2.getT())
            {
                if (trackPool.getTrackSegmentWithDetection(d1) == trackPool.getTrackSegmentWithDetection(d2))
                {
                    trackPool.cutTrackAfterDetection(d1);
                    if (trackPool.getDisplaySequence() != null)
                    {
                        trackPool.getDisplaySequence().painterChanged(null);
                    }
                    clearDetectionsAndReDisplay();
                    updateUI();
                    return;
                }
            }
        }

        // Split tracks and connect

        trackPool.createVirtualTrackWith2TrackDetection(MouseLink.detectionStart, MouseLink.detectionEnd);

        // updates GUI
        // trackPool.getSequence().painterChanged(null);
        if (trackPool.getDisplaySequence() != null)
        {
            trackPool.getDisplaySequence().painterChanged(null);
        }
        clearDetectionsAndReDisplay();
        updateUI();

    }

    /** This Internal class is used to create the mouseLink */
    static class MouseLink
    {
        static Detection detectionEnd;
        static Detection detectionStart;
        static int xstart, ystart;
        static int xend, yend;
        static boolean ispressed = false;
        // static TrackDetection TrackDetectionStart = null;
        // static TrackDetection TrackDetectionEnd = null;

        public static void clear()
        {
            ispressed = false;
            detectionStart = null;
            detectionEnd = null;

            // TrackDetectionStart = null;
            // TrackDetectionEnd = null;
        }
    }

    public void trackPainterChanged(TrackPainterChangeEvent e)
    {
        // that means we may have selected / unselected a track --> repaint the tracks panel
        redrawDetection3();
    }

    /** Called when TSP List change */
    public void TrackEditorProcessorChange(ChangeEvent e)
    {
        clearDetectionsAndReDisplay();
    }

    @Override
    public void viewerChanged(ViewerEvent event)
    {
        if (event.getDim() == DimensionId.T)
        {
            timeCursor.currentT = event.getSource().getPositionT();
            // trackPool.fireTrackEditorProcessorChange();
            // clearDetectionsAndReDisplay();
            repaint();
        }
    }

    // main listener

    public void displaySequenceChanged()
    {
        setupViewerListener();

    }

    public void setEnableLeftPanelTrackDisplay(boolean selected)
    {
        this.enableLeftPanelTrackDisplay = selected;
        redrawDetection3();

    }

    @Override
    public void viewerClosed(Viewer viewer)
    {
        setupViewerListener();
    }

    @Override
    public void viewerOpened(Viewer viewer)
    {
        setupViewerListener();

    }

    @Override
    public void sequenceOpened(Sequence sequence)
    {
        setupViewerListener();

    }

    @Override
    public void sequenceClosed(Sequence sequence)
    {
        setupViewerListener();
    }

}
