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

import org.bioimageanalysis.icy.gui.GuiUtil;
import org.bioimageanalysis.icy.gui.dialog.ActionDialog;
import org.bioimageanalysis.icy.system.logging.IcyLogger;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class TrackLengthDialog implements ActionListener {
    JTextField textField = new JTextField("3-10");
    TrackPool trackPool;

    public TrackLengthDialog(final TrackPool trackPool) {
        this.trackPool = trackPool;
        final ActionDialog actionDialog = new ActionDialog("Select by length");
        actionDialog.getContentPane().setLayout(new BoxLayout(actionDialog.getContentPane(), BoxLayout.PAGE_AXIS));

        actionDialog.getContentPane().add(GuiUtil.createLineBoxPanel(new JLabel("Use the syntax: from-to (in number of frame):")));
        actionDialog.getContentPane().add(GuiUtil.createLineBoxPanel(textField));

        actionDialog.pack();
        actionDialog.setLocationRelativeTo(null);

        actionDialog.setOkAction(this);
        actionDialog.setVisible(true);
    }

    @Override
    public void actionPerformed(final ActionEvent e) {
        final String text = textField.getText();

        final String[] values = text.split("-");
        int lowVal;
        int highVal;
        try {
            lowVal = Integer.parseInt(values[0]);
        }
        catch (final Exception e1) {
            lowVal = 0;
        }
        try {
            highVal = Integer.parseInt(values[1]);
        }
        catch (final Exception e1) {
            highVal = Integer.MAX_VALUE;
        }

        IcyLogger.debug(this.getClass(), "low val : " + lowVal, "high val : " + highVal);

        trackPool.selectTracksByLength(lowVal, highVal);
    }
}
