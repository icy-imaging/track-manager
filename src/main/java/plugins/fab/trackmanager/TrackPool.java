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

import fr.icy.Icy;
import fr.icy.gui.dialog.MessageDialog;
import fr.icy.gui.frame.progress.AnnounceFrame;
import fr.icy.model.sequence.Sequence;
import fr.icy.model.swimmingPool.SwimmingObject;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import plugins.nchenouard.spot.Detection;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * TrackPool object used for TrackEditor. A trackPool is as a pool of TrackSegment.
 *
 * @author Fabrice de Chaumont
 */
public class TrackPool {
    private static final Logger LOGGER = Logger.getLogger(TrackPool.class.getName());

    private final TrackManagerPainter tePainter;

    private final ArrayList<PluginTrackManagerProcessor> trackManagerProcessorList = new ArrayList<>();

    private final ArrayList<TrackManagerProcessorListener> trackEditorProcessorListener = new ArrayList<>();

    public TrackPool() {
        tePainter = new TrackManagerPainter(this);
    }

    public ArrayList<PluginTrackManagerProcessor> getTrackManagerProcessorList() {
        return trackManagerProcessorList;
    }

    public void addTrackProcessor(final PluginTrackManagerProcessor tep) {
        trackManagerProcessorList.add(tep);
        fireTrackEditorProcessorChange();
    }

    /**
     * Add a listener that will send changes on TrackEditorProcessor's ArrayList
     */
    public void addTrackProcessorListener(final TrackManagerProcessorListener tmpl) {
        trackEditorProcessorListener.add(tmpl);
    }

    public void computeTrackProcessor() {
        for (final PluginTrackManagerProcessor tep : trackManagerProcessorList) {
            final long startTimeInNs = System.nanoTime();
            tep.Compute();
            tep.setPerformance((int) ((System.nanoTime() - startTimeInNs) / 1000000f));
        }
    }

    public void clearTracks() {
        for (final TrackGroup trackGroup : getTrackGroupList())
            trackGroup.getTrackSegmentList().clear();

        fireTrackEditorProcessorChange();
    }

    @Contract(pure = true)
    public static boolean linkExists(final @NotNull List<Link> links, final TrackSegment start, final TrackSegment end) {
        // check if the link already exists
        for (final Link link : links)
            if ((link.start == start) && (link.end == end))
                return true;

        return false;
    }

    public static boolean isLinkExists(final List<TrackGroup> groups, final TrackSegment start, final TrackSegment end) {
        return linkExists(getLinks(groups), start, end);
    }

    public boolean isLinkExists(final TrackSegment start, final TrackSegment end) {
        return linkExists(getLinks(), start, end);
    }

    public static void createLink(final List<TrackGroup> groups, final TrackSegment start, final TrackSegment end) {
        if (!isLinkExists(groups, start, end)) {
            start.nextList.add(end);
            end.previousList.add(start);
        }
    }

    public void createLink(final TrackSegment start, final TrackSegment end) {
        if (!isLinkExists(start, end)) {
            start.nextList.add(end);
            end.previousList.add(start);
        }
    }

    /**
     * Creates a virtual track
     */
    public void createVirtualTrackWith2Detection(Detection detectionStart, Detection detectionEnd) {
        TrackSegment segmentStart = getTrackSegmentWithDetection(detectionStart);
        TrackSegment segmentEnd = getTrackSegmentWithDetection(detectionEnd);

        if (segmentStart == segmentEnd) {
            MessageDialog.showDialog("Can't link 2 detections belonging to the same track.", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        //if (segmentStart.getOwnerTrackGroup() != segmentStart.getOwnerTrackGroup()) { // TODO I think it's an error
        if (segmentStart.getOwnerTrackGroup() != segmentEnd.getOwnerTrackGroup()) {
            MessageDialog.showDialog("The two tracks should be from the same track group.", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        if (detectionStart.getT() >= detectionEnd.getT()) {
            MessageDialog.showDialog("Can't create a backward link.", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // check if detection are at start and end of their tracksegment.
        {
            if (detectionStart != segmentStart.getLastDetection()) {
                // The detection is not the last of the track.
                // Must split the start track.

                final ArrayList<TrackSegment> returnList = splitTrackSegment(segmentStart, segmentStart.getDetectionIndex(detectionStart), true);
                // should render 2 segments. The first is the one we want. We take the last detection of the first tracksegment.
                detectionStart = returnList.get(0).getLastDetection();
                segmentStart = getTrackSegmentWithDetection(detectionStart);
            }

            if (detectionEnd != segmentEnd.getFirstDetection()) {
                // The detection is not the first of the track.
                // Must split the end track.

                final ArrayList<TrackSegment> returnList = splitTrackSegment(segmentEnd, segmentEnd.getDetectionIndex(detectionEnd) - 1, true);
                // should render 2 segments. The second is the one we want. We take the first detection of the second tracksegment.
                detectionEnd = returnList.get(1).getFirstDetection();
                segmentEnd = getTrackSegmentWithDetection(detectionEnd);
            }
        }

        // System.out.println("debug: creates final link.");

        // Creates final link.
        // check if start detection is t-1 end detection.
        // if not, creates a bridge with a tracksegment of virtual detections.

        if (detectionEnd.getT() - detectionStart.getT() != 1) {
            final TrackSegment vts = new TrackSegment();

            for (int t = detectionStart.getT() + 1; t < detectionEnd.getT(); t++) {
                // linear interpolation.

                final double xstart = detectionStart.getX();
                final double ystart = detectionStart.getY();
                final double zstart = detectionStart.getZ();

                final double xend = detectionEnd.getX();
                final double yend = detectionEnd.getY();
                final double zend = detectionEnd.getZ();

                final double nbt = detectionEnd.getT() - detectionStart.getT();

                final double vx = (xend - xstart) / nbt;
                final double vy = (yend - ystart) / nbt;
                final double vz = (zend - zstart) / nbt;

                final double x = xstart + vx * (t - detectionStart.getT());
                final double y = ystart + vy * (t - detectionStart.getT());
                final double z = zstart + vz * (t - detectionStart.getT());

                final Detection detect = new Detection(x, y, z, t);
                // Detection detect = detectionStart.getDetectionEditor().createDetection( x , y , z , t );
                // if ( detect == null )
                // {
                // System.err.println("TrackPool: Can not create the track. DetectionEditorExternalTools return null on createDetection().");
                // return;
                // }
                // detect.setDeet( detectionStart.getDetectionEditor() );
                detect.setDetectionType(Detection.DETECTIONTYPE_VIRTUAL_DETECTION);
                vts.addDetection(detect);
            }

            final TrackGroup trackGroup = segmentStart.getOwnerTrackGroup();
            // System.out.println("debug trackgroup = " + trackGroup );
            final int indexSegmentStart = trackGroup.getTrackSegmentList().indexOf(segmentStart);

            // vts.setTrackGroup( segmentStart.getTrackGroup() );
            // segmentEnd.setTrackGroup( segmentStart.getTrackGroup() );

            trackGroup.getTrackSegmentList().add(indexSegmentStart, vts);
            vts.setOwnerTrackGroup(trackGroup);

            createLink(segmentStart, vts);
            createLink(vts, segmentEnd);
            new AnnounceFrame("Link(s) created", 2);
        }
        else {
            // segmentEnd.setTrackGroup( segmentStart.getTrackGroup() );
            if (isLinkExists(segmentStart, segmentEnd)) {
                removeLink(segmentStart, segmentEnd);

                new AnnounceFrame("Link deleted", 2);
            }
            else {
                createLink(segmentStart, segmentEnd);
                new AnnounceFrame("Link created", 2);
            }
        }
        // display link list
        // System.out.println("-----------");

        // ****** check and remove identical links
        // int nbIdenticalLink = 0;
        final ArrayList<Link> linkList = getLinks();
        for (final Link l1 : linkList) {
            for (final Link l2 : linkList) {
                if (l1.getStartDetection() == l2.getStartDetection())
                    if (l1.getEndDetection() == l2.getEndDetection())
                        if (l1 != l2) {
                            // nbIdenticalLink++;
                            removeLink(l1.getStartSegment(), l2.getEndSegment());
                            // System.err.println( "REMOVING DUPLICATED LINK");
                        }
            }
        }
        // System.out.println("Number of identical links: " + nbIdenticalLink );

        // for ( Link l : linkList )
        // {
        // System.out.println( "i:" + linkList.indexOf(l ) + " s:" + l.getStartDetection() + " e:" + l.getEndDetection() );
        // }
    }

    /**
     * Creates a virtual track
     */
    public void createVirtualTrackWith2TrackDetection(final Detection detectionStart, final Detection detectionEnd) {
        // check if both sides of the link are TrackDetectionComponents.
        if (detectionStart == null)
            return;
        if (detectionEnd == null)
            return;

        // check if they are not equal.
        if (detectionStart == detectionEnd)
            return;

        createVirtualTrackWith2Detection(detectionStart, detectionEnd);
    }

    /**
     * cuts track after detection
     */
    public void cutTrackAfterDetection(final Detection d) {
        final TrackSegment ts = getTrackSegmentWithDetection(d);
        splitTrackSegment(ts, ts.getDetectionList().indexOf(d), false);
        new AnnounceFrame("Track split", 2);
    }

    public void deleteSelectedLink() {
        // System.out.println("debug delete selected link");

        final ArrayList<Link> links = getLinks();

        final ArrayList<Link> linksClone = new ArrayList<>(links);

        for (final Link link : linksClone) {
            // System.out.println( "link: " + link );

            // TrackSegment tsEndtest = getTrackSegmentWithDetection(link.getEndDetection());
            // TrackSegment tsStarttest = getTrackSegmentWithDetection(link.getStartDetection());
            // System.out.println( "end ts all selected: " + tsEndtest.isAllDetectionSelected() );
            // System.out.println( "start ts all selected: " + tsStarttest.isAllDetectionSelected() );

            if (link.getStartDetection().isSelected() && link.getEndDetection().isSelected()) {
                // System.out.println("Link selected " + link );
                // System.out.println( "debug found link with selection");
                links.remove(link);
                final TrackSegment tsEnd = getTrackSegmentWithDetection(link.getEndDetection());
                final TrackSegment tsStart = getTrackSegmentWithDetection(link.getStartDetection());
                tsEnd.removePrevious(tsStart);
                tsStart.removeNext(tsEnd);

                // System.out.println("Post result:");

                // System.out.println("debug tsEnd: " + tsEnd.previousList.size() );
                // System.out.println("debug tsStart: " + tsEnd.previousList.size() );

            }
            // else
            // {
            // System.out.println("Link not selected");
            // }
        }
        fireTrackEditorProcessorChange();
    }

    public void deleteSelectedTracks() {
        beginUpdate();
        final ArrayList<TrackSegment> tsListClone = new ArrayList<>(getTrackSegmentList());
        for (final TrackSegment ts : tsListClone) {
            if (ts.isAllDetectionSelected()) {
                deleteTrack(ts);
            }
        }
        endUpdate();
        fireTrackEditorProcessorChange();
    }

    /**
     * @return null if no trackgroup contains the tracksegment
     */
    public TrackGroup getTrackGroupContainingSegment(final @NotNull TrackSegment ts) {
        return ts.getOwnerTrackGroup();

    }

    public void deleteTrack(final TrackSegment ts) {

        // System.out.println("----");
        // System.out.println("Track segment id: " + ts.toString() );

        final TrackGroup trackGroup = getTrackGroupContainingSegment(ts);
        if (trackGroup == null) {
            if (LOGGER.isLoggable(Level.WARNING))
                LOGGER.log(Level.WARNING, "Can't delete The track segment " + ts + " Reason: it is not in any trackGroup.");
            return;
        }
        trackGroup.getTrackSegmentList().remove(ts);
        final ArrayList<Link> links = getLinks();
        final ArrayList<Link> linksClone = new ArrayList<>(links);

        for (final Link link : linksClone) {
            if (ts.containsDetection(link.getStartDetection())) {
                links.remove(link);
                final TrackSegment tsEnd = getTrackSegmentWithDetection(link.getEndDetection());
                tsEnd.removePrevious(ts);
            }

            if (ts.containsDetection(link.getEndDetection())) {
                links.remove(link);
                final TrackSegment tsStart = getTrackSegmentWithDetection(link.getStartDetection());
                tsStart.removeNext(ts);
            }
        }

        ts.removeId();

        fireTrackEditorProcessorChange();

    }

    boolean isUpdating = false;

    public void beginUpdate() {
        isUpdating = true;
    }

    public void endUpdate() {
        isUpdating = false;
    }

    public void fireTrackEditorProcessorChange() {
        if (!isUpdating) {
            for (final TrackManagerProcessorListener tmpl : trackEditorProcessorListener)
                tmpl.TrackEditorProcessorChange(new ChangeEvent(this));
        }
    }

    /**
     * If only one link is linking 2 tracks, it means they could be the same track
     */
    public void fuseAllTracks() {
        // System.out.println("Start Fusion.");

        boolean fuseOperationPerformed = true; // flag to restart fuse operation from zero each time a fuse has been performed.

        while (fuseOperationPerformed) {
            fuseOperationPerformed = false;

            // System.out.println("****** re-Start Fusion.");

            final ArrayList<TrackSegment> trackSegmentListClone = new ArrayList<>(getTrackSegmentList());
            for (final TrackSegment ts : trackSegmentListClone) {
                // System.out.println("Computing trackSegment # " + trackSegmentListClone.indexOf( ts ) );
                if (ts.nextList.size() == 1) // if the track has only one successor.
                    if (ts.nextList.get(0).previousList.size() == 1) // If successor has only one predecessor.
                    {
                        // System.out.println("Fusing...");
                        fuseTrack(ts, ts.nextList.get(0)); // fuse tracks.
                        fuseOperationPerformed = true;
                        break;
                    }
            }
        }

        fireTrackEditorProcessorChange();
    }

    /**
     * Fusing 2 tracks ts1 is one step before ts2.
     */
    public void fuseTrack(final TrackSegment ts1, final @NotNull TrackSegment ts2) {
        // System.out.println("debug fuse track: fusing ");
        // System.out.println("ts1 = " + ts1);
        // System.out.println("ts2 = " + ts2);

        for (final Detection detection : ts2.getDetectionList()) {
            ts1.addDetection(detection);
            // System.out.println("Adding 1 detection to ts1. t=" + detection.getT() );
        }

        // System.out.println("Copy next List");
        ts1.nextList = new ArrayList<>(new ArrayList<>(ts2.nextList));

        // ts1.getDetectionList().addAll(ts2.getDetectionList());

        while (!ts2.getDetectionList().isEmpty()) {
            final Detection detection = ts2.getFirstDetection();
            ts2.removeDetection(detection);
            // System.out.println("Remove 1 detection to ts2. t=" + detection.getT() );
        }

        // System.out.println("Destroying ts2...");
        deleteTrack(ts2);

        fireTrackEditorProcessorChange();
    }

    public ArrayList<Detection> getAllDetection() {
        final ArrayList<Detection> detectionList = new ArrayList<>();
        for (final TrackGroup trackGroup : getTrackGroupList())
            for (final TrackSegment tracksegment : trackGroup.getTrackSegmentList())
                detectionList.addAll(tracksegment.getDetectionList());
        return detectionList;
    }

    /**
     * return distance between 2 detections
     */
    public double getDistance(final @NotNull Detection d1, final @NotNull Detection d2) {
        return getDistance(d1.getX(), d1.getY(), d1.getZ(), d2.getX(), d2.getY(), d2.getZ());
    }

    /**
     * return distance between 2 pos
     */
    public double getDistance(final double x1, final double y1, final double z1, final double x2, final double y2, final double z2) {
        return Math.sqrt((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2) + (z1 - z2) * (z1 - z2));
    }

    public ArrayList<Link> getLinks() {
        final ArrayList<Link> linkarray = new ArrayList<>();

        // System.out.println("debug : getLinks");

        for (int trackSegmentIndex = 0; trackSegmentIndex < getTrackSegmentList().size(); trackSegmentIndex++) {
            final TrackSegment ts = getTrackSegmentList().get(trackSegmentIndex);

            // for ( int i = 0 ; i < ts.previousList.size() ; i++ )
            // {
            // linkarray.add( new Link( ts.previousList.get( i ) , ts ) );
            // }
            for (int i = 0; i < ts.nextList.size(); i++) {
                linkarray.add(new Link(ts, ts.nextList.get(i)));
            }

        }

        return linkarray;
    }

    public static @NotNull List<Link> getLinks(final List<TrackGroup> groups) {
        final List<Link> result = new ArrayList<>();
        final List<TrackSegment> segments = getTrackSegmentList(groups);

        for (final TrackSegment segment : segments)
            for (int i = 0; i < segment.nextList.size(); i++)
                result.add(new Link(segment, segment.nextList.get(i)));

        return result;
    }

    // FIXME: put a WeakReference in here
    private Sequence displaySequence = null;

    public void setDisplaySequence(final Sequence displaySequence) {

        this.displaySequence = displaySequence;

        // System.out.println("display sequence changed !");

        // remove this painter from all existing sequences
        removePainter();

        // add the painter to the selected display sequence.
        if (displaySequence != null) {
            // System.out.println("adding the painter");
            displaySequence.addOverlay(tePainter);
        }
        // fire a sequenceChanged to all trackManagerProcessor
        for (final PluginTrackManagerProcessor ptmp : trackManagerProcessorList) {
            ptmp.displaySequenceChanged();
        }
        // tell to trackPanel the display sequence has changed.
        if (trackManager != null) {
            if (trackManager.trackPanel != null) {
                trackManager.trackPanel.displaySequenceChanged();
            }
        }

    }

    public Sequence getDisplaySequence() {
        return displaySequence;
    }

    TrackManager trackManager;

    public TrackManager getTrackManager() {
        return trackManager;
    }

    public TrackManagerPainter getTrackPainter() {
        return tePainter;
    }

    /**
     * @return the pool of tracksegment of all the trackgroup
     */
    public static @NotNull List<TrackSegment> getTrackSegmentList(final @NotNull List<TrackGroup> groups) {
        final List<TrackSegment> result = new ArrayList<>();

        for (final TrackGroup group : groups)
            result.addAll(group.getTrackSegmentList());

        return result;
    }

    /**
     * @return the pool of tracksegment of all the trackgroup
     */
    public ArrayList<TrackSegment> getTrackSegmentList() {
        final ArrayList<TrackSegment> trackSegmentList = new ArrayList<>();
        for (final TrackGroup group : getTrackGroupList()) {
            trackSegmentList.addAll(group.getTrackSegmentList());
        }

        return trackSegmentList;
    }

    /**
     * find the tracksegment containing the given detection
     */
    public TrackSegment getTrackSegmentWithDetection(final Detection detection) {
        final ArrayList<TrackSegment> trackSegmentList = getTrackSegmentList();

        for (final TrackSegment ts : trackSegmentList) {
            if (ts.containsDetection(detection))
                return ts;
        }

        return null;
    }

    // FIXME: 05/04/2023 does nothing
    public static @NotNull ArrayList<TrackSegment> loadTracks(final File f) {
        final ArrayList<TrackSegment> inputTrackSegmentList = new ArrayList<>();
        final ArrayList<TrackSegment> targetTrackSegmentList = new ArrayList<>();

        for (final TrackSegment ts : inputTrackSegmentList) {
            if (ts.getFirstDetection() == null || ts.getLastDetection() == null) {
                if (LOGGER.isLoggable(Level.WARNING))
                    LOGGER.log(Level.WARNING, "Warning, found and removed empty track segment");
                continue;
            }

            boolean skipTrackSegment = false;

            for (final TrackSegment ts2 : ts.nextList) {
                if (ts2.getFirstDetection() == null || ts2.getLastDetection() == null) {
                    skipTrackSegment = true;
                    break;
                }
            }

            if (skipTrackSegment) {
                if (LOGGER.isLoggable(Level.WARNING))
                    LOGGER.log(Level.WARNING, "Warning, found and removed empty track segment");
                continue;
            }

            targetTrackSegmentList.add(ts);
        }
        return targetTrackSegmentList;
    }

    public void moveTrackToIndex(final TrackSegment ts, final int index) {
        // trackSegmentList.remove( ts );
        // if ( index > trackSegmentList.size() ) index = trackSegmentList.size();
        // trackSegmentList.add( index , ts );
    }

    /**
     * remove a link between 2 track segment.
     */
    public void removeLink(final @NotNull TrackSegment start, final TrackSegment end) {
        // int nb = 0;
        // while ( start.nextList.contains( end ) )
        // {
        // System.out.println("while next");
        // nb++;
        start.nextList.remove(end);
        // }
        // while ( end.previousList.contains( start ) )
        // {
        // System.out.println("While previous");
        // nb++;
        end.previousList.remove(start);
        // }
        // if ( nb > 2 )
        // {
        // System.err.println("REMOVE WORKED SEVERAL TIMES !");
        // }
    }

    public void removeTrackProcessor(final PluginTrackManagerProcessor tep) {
        trackManagerProcessorList.remove(tep);
        fireTrackEditorProcessorChange();
    }

    /**
     * remove a listener that will send changes on TrackEditorProcessor's ArrayList
     */
    public void removeTrackProcessorListener(final TrackManagerProcessorListener tmpl) {
        trackEditorProcessorListener.remove(tmpl);
    }

    public void selectAllTracks() {
        for (final TrackSegment ts : getTrackSegmentList()) {
            ts.setAllDetectionSelected(true);
        }
        fireTrackEditorProcessorChange();
    }

    public void selectTracksByLength(final int lowVal, final int highVal) {
        for (final TrackSegment ts : getTrackSegmentList()) {
            if (ts.getDetectionList().size() >= lowVal && ts.getDetectionList().size() <= highVal) {
                ts.setAllDetectionSelected(true);
            }
        }
        fireTrackEditorProcessorChange();
    }

    public void unselectAllTracks() {
        for (final TrackSegment ts : getTrackSegmentList()) {
            ts.setAllDetectionSelected(false);
        }
        fireTrackEditorProcessorChange();
    }

    public void invertSelection() {
        for (final TrackSegment ts : getTrackSegmentList())
            for (final Detection d : ts.getDetectionList())
                d.setSelected(!d.isSelected());
        fireTrackEditorProcessorChange();
    }

    /**
     * Splits a track into 2 tracks.
     *
     * @param ts                          The track to split - Important NOTE: This track will be destructed after call.
     * @param lastIndexOfFirstTrack       Index of where the track should be split.
     * @param createLinkBetweenSplitTrack if true, creates a link between split tracks.
     * @return an ArrayList with the 2 new track created.
     * Track : TTTTTT
     * Split Index : S
     * Result : 111
     * 222
     */
    public ArrayList<TrackSegment> splitTrackSegment(TrackSegment ts, final int lastIndexOfFirstTrack, final boolean createLinkBetweenSplitTrack) {
        // System.out.println("Entering split track segment.");

        // last version, but still need to consider the track group
        final TrackSegment ts1 = new TrackSegment();
        final TrackSegment ts2 = new TrackSegment();

        // copy first segment detections.
        for (int i = 0; i < lastIndexOfFirstTrack + 1; i++) {
            // old: ts1.detectionList.add( ts.detectionList.get( i ) );
            ts1.addDetection(ts.getDetectionAt(i));
        }

        // copy first segment previous list.
        ts1.previousList.addAll(ts.previousList);

        // copy second segment detections.
        for (int i = lastIndexOfFirstTrack + 1; i < ts.getDetectionList().size(); i++) {
            // old: ts2.detectionList.add( ts.detectionList.get( i ) );
            ts2.addDetection(ts.getDetectionAt(i));
        }

        // copy second segment next list.
        ts2.nextList.addAll(ts.nextList);

        for (final TrackSegment tracksegment : getTrackSegmentList()) {
            // change pointer of others previouslisttracks to point here.
            for (TrackSegment tracksegmentprevious : tracksegment.previousList) {
                if (tracksegmentprevious == ts)
                    tracksegmentprevious = ts2;
            }
            // change pointer of others nextlisttracks to point here.
            for (TrackSegment tracksegmentnext : tracksegment.nextList) {
                if (tracksegmentnext == ts)
                    tracksegmentnext = ts1;
            }
        }

        if (createLinkBetweenSplitTrack) {
            createLink(ts1, ts2);
        }

        // this was the general index. Should find in the trackgroup.
        // int indexOfTs = getTrackSegmentList().indexOf( ts );

        // TrackGroup trackGroupContainingTs;
        final int indexOfTs = ts.getOwnerTrackGroup().getTrackSegmentList().indexOf(ts);
        // int indexOfTsInGroup = getTrackGroupContainingSegment( ts );

        ts.getOwnerTrackGroup().getTrackSegmentList().add(indexOfTs, ts2);
        ts.getOwnerTrackGroup().getTrackSegmentList().add(indexOfTs, ts1);

        ts1.setOwnerTrackGroup(ts.getOwnerTrackGroup());
        ts2.setOwnerTrackGroup(ts.getOwnerTrackGroup());

        //
        // trackSegmentList.add( indexOfTs , ts2 );
        // trackSegmentList.add( indexOfTs , ts1 );

        ts.getOwnerTrackGroup().getTrackSegmentList().remove(ts);
        ts = null;

        // System.out.println("ts1 trackGroup = " + ts1.getOwnerTrackGroup() );
        // System.out.println("ts2 trackGroup = " + ts1.getOwnerTrackGroup() );

        final ArrayList<TrackSegment> trackSegmentReturnList = new ArrayList<>();
        trackSegmentReturnList.add(ts1);
        trackSegmentReturnList.add(ts2);
        return trackSegmentReturnList;
    }

    public ArrayList<TrackGroup> getTrackGroupList() {
        final ArrayList<TrackGroup> trackGroupList = new ArrayList<>();

        for (final SwimmingObject result : resultList)
            trackGroupList.add((TrackGroup) result.getObject());

        return trackGroupList;
    }

    /**
     * List of results where trackGroup has been found to process.
     */
    ArrayList<SwimmingObject> resultList = new ArrayList<>();

    public void addResult(final @NotNull SwimmingObject result) {
        if (!(result.getObject() instanceof TrackGroup)) {
            // System.out.println("Object " + result + " is not recognized as a TrackGroup.");
            return;
        }

        resultList.add(result);

    }

    public void removeResult(final SwimmingObject result) {
        if (result == null)
            return;

        resultList.remove(result);

        if (result.getObject() instanceof final TrackGroup tg) {
            // remove track segment from hash map
            for (final TrackSegment ts : tg.getTrackSegmentList())
                ts.removeId();
        }
    }

    /**
     * removes the painter from all sequences.
     */
    public void removePainter() {
        // System.out.println("remove painter");

        final List<Sequence> sequenceList = Icy.getMainInterface().getSequencesContaining(tePainter);
        for (final Sequence sequence : sequenceList) {
            sequence.removeOverlay(tePainter);
        }
    }

    public int getTrackIndex(final TrackSegment ts) {
        return getTrackSegmentList().indexOf(ts);
    }

    /**
     * @return the timepoint corresponding to the last detection of all the trackgroup.
     */
    public int getLastDetectionTimePoint() {
        int maxT = 0;
        for (final TrackGroup tg : getTrackGroupList()) {
            for (final TrackSegment ts : tg.getTrackSegmentList()) {
                final Detection d = ts.getLastDetection();
                if (d != null) {
                    if (d.getT() > maxT)
                        maxT = d.getT();
                }
            }
        }
        return maxT;
    }

    /**
     * @return the number of track considering all the track groups.
     */
    public int getTotalNumberOfTrack() {
        int total = 0;
        for (final TrackGroup tg : getTrackGroupList()) {
            total += tg.getTrackSegmentList().size();
        }
        return total;
    }
}
