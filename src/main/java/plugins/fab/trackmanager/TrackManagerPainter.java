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

import org.bioimageanalysis.icy.common.geom.point.Point5D;
import org.bioimageanalysis.icy.common.geom.shape.ShapeUtil;
import org.bioimageanalysis.icy.gui.GraphicsUtil;
import org.bioimageanalysis.icy.gui.canvas.Canvas2D;
import org.bioimageanalysis.icy.gui.canvas.IcyCanvas;
import org.bioimageanalysis.icy.model.overlay.Overlay;
import org.bioimageanalysis.icy.model.sequence.Sequence;
import org.bioimageanalysis.icy.model.swimmingPool.SwimmingObject;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import plugins.nchenouard.spot.Detection;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Painter used to display tracks on the sequence (as overlay)
 *
 * @author Fabrice de Chaumont
 */
public class TrackManagerPainter extends Overlay {
    private final TrackPool trackPool;

    /**
     * List of Listeners
     */
    private final List<TrackPainterListener> trackPainterListener = new ArrayList<>();

    private boolean drawTracksOnSequence = true;
    private boolean subPixelicDisplay = true;

    public TrackManagerPainter(final TrackPool trackPool) {
        super("Track painter");

        this.trackPool = trackPool;
    }

    public boolean isSubPixelicDisplay() {
        return subPixelicDisplay;
    }

    public void setSubPixelicDisplay(final boolean subPixelicDisplay) {
        this.subPixelicDisplay = subPixelicDisplay;
    }

    @Override
    public void mouseClick(final @NotNull MouseEvent e, final Point5D.Double imagePoint, final IcyCanvas icyCanvas) {
        if (e.isConsumed())
            return;

        float distanceThresholdSqr = (float) icyCanvas.canvasToImageDeltaX(3);
        distanceThresholdSqr *= distanceThresholdSqr;

        // Check if a detection is around.
        for (final TrackGroup tg : trackPool.getTrackGroupList()) {
            for (final TrackSegment ts : tg.getTrackSegmentList()) {
                for (final Detection d : ts.getDetectionList()) {
                    if (d.isEnabled()) {
                        //final double dist = new Point2D.Double(p.getX(), p.getY()).distanceSq(new Point2D.Double(d.getX(), d.getY()));
                        final Point2D p2d = imagePoint.toPoint2D();
                        final double dist = new Point2D.Double(p2d.getX(), p2d.getY())
                                .distanceSq(new Point2D.Double(d.getX(), d.getY()));

                        if (dist < distanceThresholdSqr) {
                            final TrackSegment ts1 = trackPool.getTrackSegmentWithDetection(d);
                            if (ts1 != null) {
                                ts1.setAllDetectionSelected(!ts1.isAllDetectionSelected());
                                trackPool.getDisplaySequence().overlayChanged(this);
                                // so tracks in left panel will be refreshed
                                fireTrackPainterEvent();
                                e.consume();
                            }
                            break;
                        }
                    }
                }
            }
        }
    }

    /*@Override
    public void mouseClick(MouseEvent e, Point2D p, IcyCanvas icyCanvas) {
        if (e.isConsumed())
            return;

        float distanceThresholdSqr = (float) icyCanvas.canvasToImageDeltaX(3);
        distanceThresholdSqr *= distanceThresholdSqr;

        // Check if a detection is around.
        for (TrackGroup tg : trackPool.getTrackGroupList()) {
            for (TrackSegment ts : tg.getTrackSegmentList()) {
                for (Detection d : ts.getDetectionList()) {
                    if (d.isEnabled()) {
                        final double dist = new Point2D.Double(p.getX(), p.getY())
                                .distanceSq(new Point2D.Double(d.getX(), d.getY()));

                        if (dist < distanceThresholdSqr) {
                            TrackSegment ts1 = trackPool.getTrackSegmentWithDetection(d);
                            if (ts1 != null) {
                                ts1.setAllDetectionSelected(!ts1.isAllDetectionSelected());
                                trackPool.getDisplaySequence().overlayChanged(this);
                                // so tracks in left panel will be refreshed
                                fireTrackPainterEvent();
                                e.consume();
                            }
                            break;
                        }
                    }
                }
            }
        }

    }*/

    // // should switch to an hashmap to manage several 3D viewers.
    // ArrayList<vtkActor> actorList = new ArrayList<vtkActor>();
    //
    // private void draw3D(Sequence sequence, VtkCanvas canvas3D)
    // {
    //
    // canvas3D.getRenderer().SetGlobalWarningDisplay(0);
    //
    // // remove existing objects
    //
    // for (vtkActor actor : actorList)
    // {
    // canvas3D.getRenderer().RemoveActor(actor);
    // }
    //
    // // display new tracks
    //
    // for (SwimmingObject result : trackPool.resultList)
    // {
    // final TrackGroup tg = (TrackGroup) result.getObject();
    // {
    // for (TrackSegment ts : tg.getTrackSegmentList())
    // // TrackSegment ts = tg.getTrackSegmentList().get( 0 );
    // {
    // // if ( ts.getDetectionList().size() < 3 ) continue;
    // {
    // // System.out.println("should pass here uin 3D");
    // // This will be used later to get random numbers.
    // vtkMath math = new vtkMath();
    //
    // // Total number of points.
    // // int numberOfInputPoints = 20;
    //
    // // One spline for each direction.
    // vtkCardinalSpline aSplineX = new vtkCardinalSpline();
    // vtkCardinalSpline aSplineY = new vtkCardinalSpline();
    // vtkCardinalSpline aSplineZ = new vtkCardinalSpline();
    //
    // vtkPoints inputPoints = new vtkPoints();
    // Color color = null;
    // boolean somethingToDisplay = false;
    // for (int i = 0; i < ts.getDetectionList().size() - 1; i++)
    // {
    // if (ts.getDetectionList().get(i).isEnabled()
    // && ts.getDetectionList().get(i + 1).isEnabled())
    // {
    // // g.setStroke(new BasicStroke(trackWidth * 3));
    // // g.setColor(Color.white);
    // if (color == null)
    // {
    // color = ts.getDetectionList().get(i).getColor();
    // }
    //
    // double x = ts.getDetectionList().get(i).getX();
    // double y = ts.getDetectionList().get(i).getY();
    // double z = ts.getDetectionList().get(i).getZ() * canvas3D.getZScaling();
    //
    // aSplineX.AddPoint(i, x);
    // aSplineY.AddPoint(i, y);
    // aSplineZ.AddPoint(i, z);
    // inputPoints.InsertPoint(i, x, y, z);
    // somethingToDisplay = true;
    //
    // }
    // }
    //
    // if (!somethingToDisplay)
    // continue; // nothing to display
    //
    // // The following section will create glyphs for the pivot points
    // // in order to make the effect of the spline more clear.
    //
    // // Create a polydata to be glyphed.
    // vtkPolyData inputData = new vtkPolyData();
    // inputData.SetPoints(inputPoints);
    //
    // // System.out.println("nb point: " + inputPoints.GetNumberOfPoints() );
    //
    // // Use sphere as glyph source.
    // vtkSphereSource balls = new vtkSphereSource();
    // balls.SetRadius(0.2);
    // balls.SetPhiResolution(5);
    // balls.SetThetaResolution(5);
    // vtkGlyph3D glyphPoints = new vtkGlyph3D();
    //
    // // glyphPoints.SetInputConnection( balls.GetOutputPort() );
    // /*
    // * l faut en fait remplacer dans certains cas setInputData(xxx.getOutput()) par setInputConnection(xxx.getOutputPort())
    // */
    // glyphPoints.SetInputData(inputData);
    // glyphPoints.SetSourceData(balls.GetOutput());
    // // glyphPoints.SetInputConnection( balls.GetOutputPort() );
    //
    // vtkPolyDataMapper glyphMapper = new vtkPolyDataMapper();
    // glyphMapper.SetInputData(glyphPoints.GetOutput());
    //
    // vtkActor glyph = new vtkActor();
    // glyph.SetMapper(glyphMapper);
    //
    // glyph.GetProperty().SetDiffuseColor(color.getRed() / 255d, color.getGreen() / 255d,
    // color.getBlue() / 255d);
    // glyph.GetProperty().SetSpecular(.3);
    // glyph.GetProperty().SetSpecularPower(30);
    //
    // // Generate the polyline for the spline.
    // vtkPoints points = new vtkPoints();
    // vtkPolyData profileData = new vtkPolyData();
    //
    // // Number of points on the spline
    // int nbSpots = inputPoints.GetNumberOfPoints();
    // int numberOfOutputPointsOverTheSpline = nbSpots * 10;
    //
    // // Interpolate x, y and z by using the three spline filters and
    // // create new points
    //
    // // System.out.println("number of point over spline: " + numberOfOutputPointsOverTheSpline );
    //
    // for (int i = 0; i < numberOfOutputPointsOverTheSpline; i++)
    // {
    // double t = (nbSpots - 1.0) / (numberOfOutputPointsOverTheSpline - 1.0) * i;
    // points.InsertPoint(i, aSplineX.Evaluate(t), aSplineY.Evaluate(t), aSplineZ.Evaluate(t));
    // }
    //
    // // Create the polyline.
    // vtkCellArray lines = new vtkCellArray();
    // lines.InsertNextCell(numberOfOutputPointsOverTheSpline);
    // // lines.InsertNextCell( 123456 );
    // for (int i = 0; i < numberOfOutputPointsOverTheSpline; i++)
    // {
    // lines.InsertCellPoint(i);
    // }
    //
    // profileData.SetPoints(points);
    // profileData.SetLines(lines);
    //
    // // Add thickness to the resulting line.
    // vtkTubeFilter profileTubes = new vtkTubeFilter();
    // profileTubes.SetNumberOfSides(8);
    // profileTubes.SetInputData(profileData);
    // // profileTubes.SetRadius(.01);
    // profileTubes.SetRadius(0.1);
    //
    // vtkPolyDataMapper profileMapper = new vtkPolyDataMapper();
    // profileMapper.SetInputData(profileTubes.GetOutput());
    //
    // vtkActor profile = new vtkActor();
    // profile.SetMapper(profileMapper);
    //
    // // FIXME: support only 1 color for a full track.
    // profile.GetProperty().SetOpacity(1);
    // // profile.GetProperty().SetDiffuseColor(1, 0, 1);
    // profile.GetProperty().SetDiffuseColor(color.darker().getRed() / 255d,
    // color.darker().getGreen() / 255d, color.darker().getBlue() / 255d);
    //
    // profile.GetProperty().SetSpecular(.3);
    // profile.GetProperty().SetSpecularPower(30);
    //
    // // Add the actors
    // canvas3D.getRenderer().AddActor(glyph);
    // canvas3D.getRenderer().AddActor(profile);
    //
    // actorList.add(glyph);
    // actorList.add(profile);
    // }
    //
    // }
    // }
    // }
    //
    // }

    @Override
    public void paint(final Graphics2D g, final Sequence sequence, final IcyCanvas canvas) {
        // System.out.println("TP");

        if (trackPool != null) {
            if (trackPool.getTrackManager() != null) {
                if (canvas != null) {
                    if (trackPool.getTrackManager().currentT != canvas.getPositionT()) {
                        // synchronize processors now so  repaint is ok 
                        trackPool.getTrackManager().currentT = canvas.getPositionT();
                        trackPool.getTrackManager().trackPanel.recomputeProcessors();

                        // sequence.overlayChanged(this);

                        // System.out.println("Paint synchro: t " + canvas.getPositionT() );

                        // return;
                    }
                }
            }
        }

        // System.out.println("TP: now painting frame " + trackPool.getTrackManager().currentT );

        if (!isDrawTracksOnSequence())
            return;

        // if (canvas instanceof VtkCanvas)
        // {
        // draw3D(sequence, (VtkCanvas) canvas);
        // }

        if (canvas instanceof Canvas2D) {
            final Graphics2D g2 = (Graphics2D) g.create();
            final Rectangle2D bounds = new Rectangle2D.Double();
            final Line2D line = new Line2D.Double();
            final Ellipse2D ellipse = new Ellipse2D.Double();

            // emphasis all detections
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Color.RED);
            // shift 0.5 to display track in the center of pixel
            g2.translate(0.5d, 0.5d);

            final float trackWidth = (float) canvas.canvasToImageLogDeltaX(2);

            g2.setStroke(new BasicStroke(trackWidth));

            // build the list of line section
            final List<Detection> allDetections = new ArrayList<>();
            final List<TrackLineSection> lineSections = new ArrayList<>();
            final List<TrackLineSection> connectorSections = new ArrayList<>();

            if (trackPool != null) {
                for (final SwimmingObject result : trackPool.resultList) {
                    final TrackGroup tg = (TrackGroup) result.getObject();
                    for (final TrackSegment ts : tg.getTrackSegmentList()) {
                        final List<Detection> detectionList = ts.getDetectionList();

                        allDetections.addAll(detectionList);

                        // line sections
                        if (detectionList.size() > 1) {
                            Detection startDetection = detectionList.get(0);

                            for (int i = 1; i < ts.getDetectionList().size(); i++) {
                                final Detection endDetection = detectionList.get(i);

                                lineSections.add(new TrackLineSection(startDetection, endDetection));

                                startDetection = endDetection;
                            }
                        }

                        // connector between 2 tracks
                        if (ts.getLastDetection().isEnabled()) {
                            for (final TrackSegment tsNext : ts.nextList) {
                                final Detection endDetection = tsNext.getFirstDetection();

                                if (endDetection.isEnabled())
                                    connectorSections.add(new TrackLineSection(ts.getLastDetection(), endDetection));
                            }
                        }
                    }
                }
            }

            // display background of tracks if selected
            g2.setStroke(new BasicStroke(trackWidth * 3));
            g2.setColor(Color.white);

            for (final TrackLineSection lineSection : lineSections) {
                if (lineSection.isEnabled() && lineSection.isSelected() && GraphicsUtil.isVisible(g2, lineSection.getBounds(bounds, trackWidth * 2)))
                    g2.draw(lineSection.getLine2D(line));
            }

            // display tracks
            g2.setStroke(new BasicStroke(trackWidth));

            for (final TrackLineSection lineSection : lineSections) {
                if (lineSection.isEnabled() && GraphicsUtil.isVisible(g2, lineSection.getBounds(bounds, trackWidth))) {
                    g2.setColor(lineSection.getColor());
                    g2.draw(lineSection.getLine2D(line));
                }
            }

            // display link between tracks
            g2.setColor(Color.green);

            for (final TrackLineSection lineSection : connectorSections) {
                if (GraphicsUtil.isVisible(g2, lineSection.getBounds(bounds, trackWidth)))
                    g2.draw(lineSection.getLine2D(line));
            }

            // display detections
            for (final Detection detection : allDetections) {
                // current detection is visible
                if (detection.isEnabled() && (detection.getT() == canvas.getPositionT()))
                // && (Math.round(detection.getZ()) == canvas.getPositionZ()))
                {
                    final double x = detection.getX();
                    final double y = detection.getY();
                    bounds.setFrame(x - (trackWidth * 1.5d), y - (trackWidth * 1.5d), trackWidth * 3, trackWidth * 3);

                    if (GraphicsUtil.isVisible(g2, bounds)) {
                        ellipse.setFrame(bounds);

                        g2.setColor(detection.getColor());
                        g2.draw(ellipse);
                    }
                }
            }

            g2.dispose();
        }
    }

    /**
     * add a listener
     */
    public void addTrackPainterListener(final TrackPainterListener trackPainterListener) {
        this.trackPainterListener.add(trackPainterListener);
    }

    /**
     * remove a listener
     */
    public void removeTrackPainterListener(final TrackPainterListener trackPainterListener) {
        this.trackPainterListener.remove(trackPainterListener);
    }

    // /** fire event with a given TrackPainterChangeEvent */
    // private void fireTrackPainterEvent(TrackPainterChangeEvent e)
    // {
    // for (TrackPainterListener tpl : trackPainterListener)
    // {
    // tpl.trackPainterChanged(e);
    // }
    // }

    /**
     * fire event with a non-specific TrackPainterChangeEvent
     */
    private void fireTrackPainterEvent() {
        for (final TrackPainterListener tpl : trackPainterListener) {
            tpl.trackPainterChanged(new TrackPainterChangeEvent(this));
        }
    }

    public boolean isDrawTracksOnSequence() {
        return drawTracksOnSequence;
    }

    public void setDrawTracksOnSequence(final boolean drawTracksOnSequence) {
        this.drawTracksOnSequence = drawTracksOnSequence;
    }

    // @Override
    // public final void sequenceChanged(SequenceEvent event)
    // {
    // switch (event.getSourceType())
    // {
    // case SEQUENCE_META:
    // String metadataName = (String) event.getSource();
    //
    // if ( StringUtil.equals(metadataName, Sequence.ID_PIXEL_SIZE_Z ))
    // {

    // adjustZScaling( getSequence().getPixelSizeZ() );
    // }
    //
    // break;
    // }
    // }

    // @Override
    // public void sequenceClosed(Sequence sequence)
    // {
    // }

    private record TrackLineSection(Detection start, Detection end) {
        @Contract("_ -> param1")
        public @NotNull Line2D getLine2D(final @NotNull Line2D line) {
            line.setLine(start.getX(), start.getY(), end.getX(), end.getY());
            return line;
        }

        public Color getColor() {
            return start.getColor();
        }

        @Contract("_, _ -> param1")
        public @NotNull Rectangle2D getBounds(final @NotNull Rectangle2D bounds, final float stroke) {
            bounds.setFrameFromDiagonal(start.getX(), start.getY(), end.getX(), end.getY());
            ShapeUtil.enlarge(bounds, stroke, stroke, true);
            return bounds;
        }

        public boolean isSelected() {
            return start.isSelected() && end.isSelected();
        }

        public boolean isEnabled() {
            return start.isEnabled() && end.isEnabled();
        }
    }
}
