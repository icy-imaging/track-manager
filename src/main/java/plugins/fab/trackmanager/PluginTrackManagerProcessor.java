/*
 * Copyright 2010-2023 Institut Pasteur.
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
 * along with ICY. If not, see <https://www.gnu.org/licenses/>.
 */
package plugins.fab.trackmanager;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

import icy.gui.component.PopupPanel;
import icy.gui.util.GuiUtil;
import icy.plugin.abstract_.Plugin;

public abstract class PluginTrackManagerProcessor extends Plugin {

    /**
     * Panel to used by inherited class to display data
     */
    protected JPanel panel = new JPanel();
    private final PopupPanel mainPanel = new PopupPanel("no name");

    protected TrackPool trackPool;
    protected JCheckBox enabledCheckBox = new JCheckBox("Enable");
    protected JButton closeButton = new JButton("X");
    protected JButton moveUpButton = new JButton("Up");
    protected JButton moveDownButton = new JButton("Down");
    protected JLabel performanceLabel = new JLabel("----");
    ActionButton actionButton = new ActionButton();

    public PluginTrackManagerProcessor() {
        mainPanel.expand();

        enabledCheckBox.setSelected(true);
        enabledCheckBox.addActionListener(actionButton);

        JPanel commandPanel = GuiUtil.createLineBoxPanel(enabledCheckBox, Box.createHorizontalGlue(), performanceLabel,
                moveUpButton, moveDownButton, closeButton);

        closeButton.addActionListener(actionButton);
        closeButton.setToolTipText("Close processor");
        moveUpButton.setToolTipText("Move up processor");
        moveDownButton.setToolTipText("Move down processor");
        performanceLabel.setToolTipText("Number of milliseconds used by this processor for its last run");

        moveUpButton.addActionListener(actionButton);
        moveDownButton.addActionListener(actionButton);

        mainPanel.getMainPanel().setLayout(new BoxLayout(mainPanel.getMainPanel(), BoxLayout.PAGE_AXIS));

        mainPanel.getMainPanel().add(GuiUtil.createLineBoxPanel(commandPanel));
        mainPanel.getMainPanel().add(GuiUtil.createLineBoxPanel(panel));
    }

    protected void setName(String name) {
        mainPanel.setTitle(name);
    }

    protected boolean isEnabled() {
        return enabledCheckBox.isSelected();
    }

    class ActionButton implements ActionListener {
        public void actionPerformed(ActionEvent e) {

            if (e.getSource() == closeButton) {
                Close();
                removeTrackProcessor();
                trackPool.fireTrackEditorProcessorChange();
            }
            if (e.getSource() == enabledCheckBox) {
                trackPool.fireTrackEditorProcessorChange();
            }
            if (e.getSource() == moveUpButton) {
                trackPool.getTrackManager().moveTrackProcessor(PluginTrackManagerProcessor.this, -1);
            }
            if (e.getSource() == moveDownButton) {
                trackPool.getTrackManager().moveTrackProcessor(PluginTrackManagerProcessor.this, +1);
            }
        }
    }

    /**
     * Override this function to perform special action before destroying the TrackProcessor.
     */
    public abstract void Close();

    /**
     * Override this to affect data on detection (color (...) )
     */
    public abstract void Compute();

    public void setPerformance(int ms) {
        performanceLabel.setText(ms + " ms ");
    }

    private void removeTrackProcessor() {
        trackPool.removeTrackProcessor(this);
        JPanel parent = (JPanel) mainPanel.getParent();
        parent.remove(mainPanel);
        parent.revalidate();
    }

    /**
     * Return the Panel Control of this TrackProcessor
     */
    public JPanel getPanel() {
        return mainPanel;
    }

    @Override
    protected void finalize() throws Throwable {
        trackPool = null;
        super.finalize();
    }

    public final void setTrackPool(TrackPool trackPool) {
        this.trackPool = trackPool;
    }

    abstract public void displaySequenceChanged();

}
