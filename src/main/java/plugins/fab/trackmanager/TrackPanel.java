/*
 * Copyright (c) 2010-2026. Institut Pasteur.
 *
 * This file is part of Icy.
 * Icy is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Icy is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Icy. If not, see <https://www.gnu.org/licenses/>.
 */

package plugins.fab.trackmanager;

import org.bioimageanalysis.icy.Icy;
import org.bioimageanalysis.icy.gui.listener.GlobalSequenceListener;
import org.bioimageanalysis.icy.gui.listener.GlobalViewerListener;
import org.bioimageanalysis.icy.gui.viewer.Viewer;
import org.bioimageanalysis.icy.gui.viewer.ViewerEvent;
import org.bioimageanalysis.icy.gui.viewer.ViewerListener;
import org.bioimageanalysis.icy.model.overlay.Overlay;
import org.bioimageanalysis.icy.model.sequence.DimensionId;
import org.bioimageanalysis.icy.model.sequence.Sequence;
import org.bioimageanalysis.icy.system.logging.IcyLogger;
import org.bioimageanalysis.icy.system.thread.ThreadUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import plugins.nchenouard.spot.Detection;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Panel managing Tracks and users interactions.
 *
 * @author Fabrice de Chaumont
 */
public class TrackPanel extends JPanel implements MouseListener, MouseMotionListener, TrackPainterListener, TrackManagerProcessorListener, ViewerListener, GlobalSequenceListener, GlobalViewerListener, Runnable {
    TimeCursor timeCursor = new TimeCursor();

    /**
     * Dimension of a single detection
     */
    private Dimension detectDim = new Dimension(10, 10);

    private TrackPool trackPool;

    public TrackPanel(final @NotNull TrackPool trackPool, final boolean enableLeftPanelTrackDisplay) {
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

        // System.out.println("TEST trackpanel");
    }

    void setupViewerListener() {
        // remove all listener

        for (final Viewer viewer : Icy.getMainInterface().getViewers()) {
            viewer.removeListener(this);
        }

        // listen to proper viewers

        final Sequence sequence = trackPool.getDisplaySequence();
        if (sequence == null)
            return;

        for (final Viewer viewer : Icy.getMainInterface().getViewers(sequence)) {
            viewer.addListener(this);
        }
    }

    @Override
    public void mouseClicked(final MouseEvent e) {
        if (!enableLeftPanelTrackDisplay)
            return;

        MouseLink.clear();

        // Watch if a detection exists for selection.
        final TrackSegment ts = getTrackSegmentAt(e.getY());
        if (ts == null)
            return;
        ts.setAllDetectionSelected(!ts.isAllDetectionSelected());
        redrawDetection3();

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

    private TrackSegment getTrackSegmentAt(int y) {
        TrackSegment tsResult = null;

        y = y / getDetectDim().height;

        int currentLine = 0;

        loop:
        for (final TrackGroup trackGroup : trackPool.getTrackGroupList())
            for (final TrackSegment ts : trackGroup.getTrackSegmentList()) {
                if (currentLine == y) {
                    tsResult = ts;
                    break loop;
                }
                currentLine++;
            }

        return tsResult;
    }

    private @Nullable Detection getDetectionAt(final int x, final int y) {
        final TrackSegment ts = getTrackSegmentAt(y);

        if (ts == null)
            return null;

        final int t = x / getDetectDim().width;

        return ts.getDetectionAtTime(t);
    }

    @Override
    public void mouseEntered(final MouseEvent e) {

    }

    @Override
    public void mouseExited(final MouseEvent e) {

    }

    @Override
    public void mousePressed(final MouseEvent e) {
        if (!enableLeftPanelTrackDisplay)
            return;

        MouseLink.clear();

        MouseLink.xStart = e.getX();
        MouseLink.yStart = e.getY();

        MouseLink.xEnd = e.getX();
        MouseLink.yEnd = e.getY();

        // System.out.println( "debug info: mouse pressed.");

        final Component component = getComponentAt(MouseLink.xStart, MouseLink.yStart);

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

        final Detection detection = getDetectionAt(e.getX(), e.getY());

        // System.out.println( "detection at click : " + detection );

        if (detection != null) {
            MouseLink.xStart = e.getX();
            MouseLink.yStart = e.getY();
            MouseLink.detectionStart = detection;
            MouseLink.isPressed = true;
            // System.out.println("Debug detection mouse link start : " + detection );
        }

        // System.out.println("debug Track selected : " + track );

        // manage track and time drag. Time has priority.
        if (component == this) {
            if (timeCursor.contains(e.getX(), e.getY())) {
                // enables time drag.
                timeCursor.dragging = true;
            }
            else {
                // look if at least one track exists.
                boolean oneAtLeast = false;
                for (final TrackGroup trackGroup : trackPool.getTrackGroupList()) {
                    if (!trackGroup.getTrackSegmentList().isEmpty()) {
                        oneAtLeast = true;
                        break;
                    }
                }

                // enables trackDrag.
                // if ( trackPool.getTrackSegmentList().size() != 0 )
                if (oneAtLeast) {
                    TrackDrag.dragging = true;
                    TrackDrag.ts = getCurrentTrackSegmentWithY(e.getY());
                    repaint();
                }
            }
        }

    }

    /**
     * Manage time cursor.
     */
    private class TimeCursor {
        int currentT = 0;
        boolean dragging = false;

        boolean contains(final int x, final int y) {
            final Rectangle r = new Rectangle(currentT * (int) detectDim.getWidth(), 0, (int) detectDim.getWidth(),
                    Integer.MAX_VALUE);
            return r.contains(x, y);
        }
    }

    /**
     * Returns the track at Y //FIXME !
     */
    private TrackSegment getCurrentTrackSegmentWithY(final int Y) {
        // System.out.println("drag a refaire.");
        // return null;
        return trackPool.getTrackSegmentList().get(getCurrentTrackSegmentIndexWithY(Y));
    }

    /**
     * Returns the number of the track with Y //FIXME !
     */
    private int getCurrentTrackSegmentIndexWithY(final int Y) {
        // System.out.println("drag a refaire.");
        // return 0;
        int index = (int) (Y / detectDim.getHeight());
        if (index < 0)
            index = 0;
        if (index >= trackPool.getTrackSegmentList().size())
            index = trackPool.getTrackSegmentList().size() - 1;
        return index;
    }

    /**
     * class use to record Track Drag states.
     */
    private static class TrackDrag {
        static boolean dragging = false;
        static TrackSegment ts = null;
    }

    @Override
    public void mouseReleased(final @NotNull MouseEvent e) {
        MouseLink.xEnd = e.getX();
        MouseLink.yEnd = e.getY();

        // System.out.println("mouse released");

        if (MouseLink.isPressed) {

            // System.out.println("Mouse link is pressed");

            final Detection detection = getDetectionAt(e.getX(), e.getY());
            if (detection != null) {
                MouseLink.xEnd = e.getX();
                MouseLink.yEnd = e.getY();
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

        if (TrackDrag.dragging) {
            TrackDrag.dragging = false;
            repaint();
        }

        if (timeCursor.dragging) {
            timeCursor.dragging = false;
            repaint();
        }
    }

    @Override
    public void mouseDragged(final MouseEvent e) {
        if (MouseLink.isPressed) {
            MouseLink.xEnd = e.getX();
            MouseLink.yEnd = e.getY();

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
        if (TrackDrag.dragging) {
            trackPool.moveTrackToIndex(TrackDrag.ts, getCurrentTrackSegmentIndexWithY(e.getY()));
            clearDetectionsAndReDisplay();
        }

        // if time dragging , moves times.
        if (timeCursor.dragging) {
            timeCursor.currentT = (int) (e.getX() / detectDim.getWidth());

            // System.out.println( TimeCursor.currentT );
            // System.out.println( "track editor : " + trackPool.getTrackEditor() );

            // FIXME : remettre ces sécurités.
            if (timeCursor.currentT < 0)
                timeCursor.currentT = 0;
            // if ( TimeCursor.currentT > trackPool.getSequence().getLength() -1 )
            // TimeCursor.currentT = trackPool.getSequence().getLength() - 1;
            // trackPool.getSequence().setSelectedT( TimeCursor.currentT );

            trackPool.getTrackManager().timeCursorChanged(timeCursor.currentT);
        }
    }

    @Override
    public void mouseMoved(final MouseEvent e) {

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

    public Dimension getDetectDim() {
        return detectDim;
    }

    public void setDetectDim(final Dimension detectDim) {
        this.detectDim = detectDim;
    }

    public void setTrackPool(final TrackPool trackPool) {
        this.trackPool = trackPool;
        clearDetectionsAndReDisplay();
    }

    /**
     * Clear Detections and redisplay them.
     */
    private void clearDetectionsAndReDisplay() {
        // do it in background
        ThreadUtil.bgRunSingle(this);
    }

    /**
     * Recompute all processors
     */
    public void recomputeProcessors() {
        // important to do it on EDT (otherwise we can get death lock)
        ThreadUtil.invokeNow(() -> {
            // reset color of detection
            for (final Detection d : trackPool.getAllDetection())
                d.reset();
            // then execute processors (important to do the whole in EDT so painting always happen after all processes)
            trackPool.computeTrackProcessor();
        });
    }

    @Override
    public void run() {
        // refresh processors
        recomputeProcessors();
        // redraw left panel
        redrawDetection3();

        // update painters
        final Sequence sequence = trackPool.getDisplaySequence();
        if (sequence != null)
            //sequence.painterChanged(null);
            sequence.overlayChanged((Overlay) null);
    }

    // boolean disableLeftPanel= false;

    /**
     * Re-create the tracking image.
     */
    public void redrawDetection3() {
        if (!enableLeftPanelTrackDisplay)
            return;

        // if ( disableLeftPanel == true ) return;

        final int maxSizeT = trackPool.getLastDetectionTimePoint();
        final int totalNumberOfTrack = trackPool.getTotalNumberOfTrack();

        // check if size is too big

        // int maxSize = 1000000000; // should be 2 000 000 000
        final int maxSize = 100000000; // should be 2 000 000 000
        final int width = getDetectDim().width * (maxSizeT + 1);
        int height = getDetectDim().height * totalNumberOfTrack;

        if (width * height > maxSize) {
            height = maxSize / width;
            IcyLogger.warn(this.getClass(), "too much track to display them all in trackManager left panel.", "Still, they all appear on the sequence.");
        }

        // if ( trackingPanelSize.width * trackingPanelSize.height > maxSize )
        // {
        // trackingPanelSize.height = maxSize / trackingPanelSize.width ;
        // }

        final Dimension trackingPanelSize = new Dimension(width, height);
        this.setPreferredSize(trackingPanelSize);
        revalidate();

        // drop image if dimensions have changed.
        if (bufferedImage != null)
            if ((bufferedImage.getWidth() != trackingPanelSize.width)
                    || (bufferedImage.getHeight() != trackingPanelSize.height)) {
                bufferedImage = null;
            }

        // create cache image if it does not exist.
        if (bufferedImage == null) {
            if (!(trackingPanelSize.width == 0 || trackingPanelSize.height == 0)) {
                try {
                    bufferedImage = new BufferedImage(trackingPanelSize.width, trackingPanelSize.height,
                            BufferedImage.TYPE_INT_RGB);
                }
                catch (final OutOfMemoryError e) {
                    IcyLogger.error(
                            this.getClass(),
                            e,
                            "can't create " + trackingPanelSize.width + "x" + trackingPanelSize.height + " bitmap for track representation in trackPanel.",
                            "left panel disabled"
                    );
                    enableLeftPanelTrackDisplay = false;
                    return;
                }
            }
            else {
                // no track to display.
                return;
            }
        }

        final Graphics2D g = bufferedImage.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // fill background
        final Color c1 = new Color(0.8f, 0.8f, 0.8f);
        final Color c2 = new Color(0.9f, 0.9f, 0.9f);
        int offsetY = 0;
        boolean switchBackground = true;
        g.setFont(new Font("Arial", Font.BOLD, detectDim.height - 2));
        for (final TrackGroup trackGroup : trackPool.getTrackGroupList())
            for (int tracknb = 0; tracknb < trackGroup.getTrackSegmentList().size(); tracknb++) {
                // display consecutive horizontal bars.

                switchBackground = !switchBackground;
                if (switchBackground) {
                    g.setColor(c1);
                }
                else {
                    g.setColor(c2);
                }

                g.fillRect(0, detectDim.height * offsetY, detectDim.width * (maxSizeT + 1), detectDim.height);
                offsetY++;

            }

        // draw detections

        int currentLine = 0; // current track line ( y offset )

        final HashMap<Detection, Point2D> detectionHashMap = new HashMap<>();

        for (final TrackGroup trackGroup : trackPool.getTrackGroupList())
            for (final TrackSegment ts : trackGroup.getTrackSegmentList())
            // for ( int trackSegmentIndex = 0 ; trackSegmentIndex <
            // trackPool.getTrackSegmentList().size() ; trackSegmentIndex ++ )
            // for ( int trackSegmentIndex = startTrackSegmentToDisplay ; trackSegmentIndex <
            // lastTrackSegmentToDisplay ; trackSegmentIndex ++ )
            {
                // System.out.println("tracksegment... dsf " + ligne + " " +
                // ts.detectionList.size() );
                // TrackSegment ts = trackPool.getTrackSegmentList().get( trackSegmentIndex );

                for (int detectionIndex = 0; detectionIndex < ts.getDetectionList().size(); detectionIndex++) {
                    final Detection d = ts.getDetectionList().get(detectionIndex);

                    // TrackDetection td = new TrackDetection(d);

                    // specification de la taille des panels representant les detections ici.
                    // td.setBounds(0 + getDetectDim().width * d.getT(), 0 + getDetectDim().height * ligne, -0
                    // + getDetectDim().width, -0 + getDetectDim().height);
                    // add(td);

                    final Rectangle2D bounds = new Rectangle2D.Double(
                            //0 + getDetectDim().width * d.getT(),
                            getDetectDim().width * d.getT(),
                            //0 + getDetectDim().height * currentLine,
                            getDetectDim().height * currentLine,
                            //-0 + getDetectDim().width,
                            getDetectDim().width,
                            //-0 + getDetectDim().height
                            getDetectDim().height
                    );
                    drawDetection(g, d, bounds);

                    detectionHashMap.put(d,
                            new Point2D.Float(getDetectDim().width * d.getT(), getDetectDim().height * currentLine));

                }
                currentLine++;
            }

        // draw links between tracks

        final Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(Color.red);
        g2.setStroke(new BasicStroke(3));

        final ArrayList<Link> links = trackPool.getLinks();
        final int detectHeight = getDetectDim().height / 2;
        final int detectWidth = getDetectDim().width / 2;

        for (final Link link : links) {
            final Detection startDetection = link.getStartDetection();
            final Detection endDetection = link.getEndDetection();

            final Point2D startDetectionPoint2D = detectionHashMap.get(startDetection);
            final Point2D endDetectionPoint2D = detectionHashMap.get(endDetection);

            if (startDetectionPoint2D != null && endDetectionPoint2D != null) {
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
        //int offsetY = 1;
        offsetY = 1;
        g.setColor(Color.white);
        for (final TrackGroup trackGroup : trackPool.getTrackGroupList()) {
            for (int tracknb = 0; tracknb < trackGroup.getTrackSegmentList().size(); tracknb++) {
                g.drawString(trackGroup.getDescription() + " # " + tracknb, 10, detectDim.height * offsetY - 2);
                offsetY++;
            }
        }

        repaint();
    }

    private void drawDetection(final @NotNull Graphics2D g, final @NotNull Detection d, final Rectangle2D bounds) {
        g.setColor(d.getColor());
        g.fill(bounds);
        g.setColor(d.getColor().darker());
        g.draw(bounds);

        if (d.isSelected()) {
            g.setStroke(new BasicStroke(3));
            g.setColor(Color.white);
            g.draw(bounds);
            g.setStroke(new BasicStroke(1));
        }

        if (d.getDetectionType() == Detection.DETECTIONTYPE_VIRTUAL_DETECTION) {
            g.setStroke(new BasicStroke(2));
            g.setColor(Color.white);
            g.drawLine((int) bounds.getMinX(), (int) bounds.getMinY(), (int) bounds.getMaxX(), (int) bounds.getMaxY());
            g.setStroke(new BasicStroke(1));
        }
    }

    BufferedImage bufferedImage;

    boolean enableLeftPanelTrackDisplay;

    /**
     * Paint the tracking Panel
     */
    @Override
    protected void paintComponent(final Graphics g1) {
        super.paintComponent(g1);
        final Graphics2D g = (Graphics2D) g1;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (!enableLeftPanelTrackDisplay) {
            g.clearRect(0, 0, this.getWidth(), this.getHeight());
            g.drawString("Display disabled. Enable selecting Display > Enable Track Panel", 20, 20);
            return;
        }

        final int totalNumberOfTrack = trackPool.getTotalNumberOfTrack();
        if (totalNumberOfTrack == 0) {
            g.drawString("No track to display", 20, 20);
        }

        if (bufferedImage == null)
            return;

        g.drawImage(bufferedImage, null, 0, 0);

        // draw MouseLink
        if (MouseLink.isPressed) {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setStroke(new BasicStroke(2));
            g.setColor(Color.red);
            g.drawLine(MouseLink.xStart, MouseLink.yStart, MouseLink.xEnd, MouseLink.yEnd);
            g.setStroke(new BasicStroke(1));
        }

        // draw current Time Cursor.
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, .5f));
        g.setColor(Color.red);
        g.fillRect(getDetectDim().width * timeCursor.currentT, getVisibleRect().y, getDetectDim().width, getVisibleRect().height);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
    }

    /**
     * Should be called when a mouse-link operation has been finished
     */
    public void finishMouseConnexion() {
        // Look for particular case where the detection belongs to the same TrackSegment, and are
        // joined
        // --> cut tracksegment.

        // System.out.println("------ entering finishMouseConnexion");
        // System.out.println("MouseLink start : " + MouseLink.detectionStart );
        // System.out.println("MouseLink end : " + MouseLink.detectionEnd );

        if (MouseLink.detectionStart != null && MouseLink.detectionEnd != null) {
            // System.out.println("Enter detect start & end valid.");

            final Detection d1 = MouseLink.detectionStart;
            final Detection d2 = MouseLink.detectionEnd;
            if (d1.getT() + 1 == d2.getT()) {
                if (trackPool.getTrackSegmentWithDetection(d1) == trackPool.getTrackSegmentWithDetection(d2)) {
                    trackPool.cutTrackAfterDetection(d1);
                    if (trackPool.getDisplaySequence() != null) {
                        //trackPool.getDisplaySequence().painterChanged(null);
                        trackPool.getDisplaySequence().overlayChanged((Overlay) null);
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
        if (trackPool.getDisplaySequence() != null) {
            //trackPool.getDisplaySequence().painterChanged(null);
            trackPool.getDisplaySequence().overlayChanged((Overlay) null);
        }
        clearDetectionsAndReDisplay();
        updateUI();
    }

    /**
     * This Internal class is used to create the mouseLink
     */
    static class MouseLink {
        static Detection detectionEnd;
        static Detection detectionStart;
        static int xStart, yStart;
        static int xEnd, yEnd;
        static boolean isPressed = false;
        // static TrackDetection TrackDetectionStart = null;
        // static TrackDetection TrackDetectionEnd = null;

        public static void clear() {
            isPressed = false;
            detectionStart = null;
            detectionEnd = null;

            // TrackDetectionStart = null;
            // TrackDetectionEnd = null;
        }
    }

    @Override
    public void trackPainterChanged(final TrackPainterChangeEvent e) {
        // that means we may have selected / unselected a track --> repaint the tracks panel
        redrawDetection3();
    }

    /**
     * Called when TSP List change
     */
    @Override
    public void TrackEditorProcessorChange(final ChangeEvent e) {
        clearDetectionsAndReDisplay();
    }

    @Override
    public void viewerChanged(final @NotNull ViewerEvent event) {
        if (event.getDim() == DimensionId.T) {
            timeCursor.currentT = event.getSource().getPositionT();
            // trackPool.fireTrackEditorProcessorChange();
            // clearDetectionsAndReDisplay();
            repaint();
        }
    }

    // main listener

    public void displaySequenceChanged() {
        setupViewerListener();
    }

    public void setEnableLeftPanelTrackDisplay(final boolean selected) {
        this.enableLeftPanelTrackDisplay = selected;
        redrawDetection3();
    }

    @Override
    public void viewerClosed(final Viewer viewer) {
        setupViewerListener();
    }

    @Override
    public void viewerOpened(final Viewer viewer) {
        setupViewerListener();
    }

    @Override
    public void sequenceOpened(final Sequence sequence) {
        setupViewerListener();
    }

    @Override
    public void sequenceClosed(final Sequence sequence) {
        setupViewerListener();
    }
}
