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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

import javax.swing.JPanel;

import plugins.nchenouard.spot.Detection;

/**
 * Display a single detection of a track. Used by TrackPanel / TrackEditor
 * 
 * @author fab
 * @deprecated not anymore a panel or an objet to speed up the rendering process.
 */
public class TrackDetection extends JPanel
{

    private static final long serialVersionUID = 7254806428248130683L;
    private boolean Selected = false;
    private Detection detection;

    public TrackDetection(Detection detection)
    {
        super();
        this.detection = detection;
        // if ( detection.getDetectionType() == Detection.DETECTIONTYPE_REAL_DETECTION ) color =
        // Color.blue;
        // if ( detection.getDetectionType() == Detection.DETECTIONTYPE_VIRTUAL_DETECTION ) color =
        // Color.orange;

    }

    public Detection getDetection()
    {
        return detection;
    }

    public void setSelected(boolean Selected)
    {
        this.Selected = Selected;
        this.updateUI();
    }

    public boolean isSelected(boolean Selected)
    {
        return Selected;
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;

        // Draw background
        Graphics2D g2D = (Graphics2D) g;
        Point2D.Float p1 = new Point2D.Float(1.f, getHeight() * 1.3f);
        Point2D.Float p2 = new Point2D.Float(1.f, getHeight() / 2f);
        GradientPaint g1 = new GradientPaint(p1, Color.WHITE, p2, detection.getColor(), true);
        Rectangle2D.Float rect1 = new Rectangle2D.Float(0, 0, getWidth(), getHeight());
        g2D.setPaint(g1); // Gradient color fill
        g2D.fill(rect1); // Fill the rectangle

        // Draw plot
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(Color.black);
        Shape shape = new Rectangle2D.Float(getWidth() / 3f, getHeight() / 3f, getWidth() - 2 * getWidth() / 3f,
                getHeight() - 2 * getHeight() / 3f);
        g2.fill(shape);

        // Draw red square if selected for drag and drop

        if (Selected)
        {
            g2.setColor(Color.red);
            g2.drawRect(1, 1, getWidth() - 3, getHeight() - 3);
        }

        // Draw square if depending detection is selected.

        if (detection.isSelected())
        {
            g2.setStroke(new BasicStroke(4));
            g2.setColor(Color.black);
            g2.drawRect(0, 0, getWidth(), getHeight());
            g2.setStroke(new BasicStroke(1));
        }
        if (detection.getDetectionType() == Detection.DETECTIONTYPE_VIRTUAL_DETECTION)
        {
            g2.setStroke(new BasicStroke(2));
            g2.setColor(Color.white);
            g2.drawLine(0, 0, getWidth(), getHeight());
            g2.setStroke(new BasicStroke(1));
        }

    }

}
