/*
 * Copyright (c) 2010-2025. Institut Pasteur.
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
import fr.icy.extension.ExtensionLoader;
import fr.icy.extension.plugin.PluginDescriptor;
import fr.icy.extension.plugin.abstract_.PluginActionable;
import fr.icy.extension.plugin.annotation_.IcyPluginName;
import fr.icy.gui.GuiUtil;
import fr.icy.gui.component.ComponentUtil;
import fr.icy.gui.component.menu.IcyPluginMenuItem;
import fr.icy.gui.component.panel.PopupPanel;
import fr.icy.gui.dialog.MessageDialog;
import fr.icy.gui.frame.IcyFrame;
import fr.icy.gui.frame.IcyFrameEvent;
import fr.icy.gui.frame.IcyFrameListener;
import fr.icy.gui.frame.progress.AnnounceFrame;
import fr.icy.gui.sequence.SequenceChooser;
import fr.icy.gui.viewer.Viewer;
import fr.icy.io.FileUtil;
import fr.icy.io.xml.XMLUtil;
import fr.icy.model.sequence.Sequence;
import fr.icy.model.swimmingPool.SwimmingObject;
import fr.icy.model.swimmingPool.SwimmingPoolEvent;
import fr.icy.model.swimmingPool.SwimmingPoolEventType;
import fr.icy.model.swimmingPool.SwimmingPoolListener;
import fr.icy.network.NetworkUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import plugins.nchenouard.spot.Detection;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

/**
 * @author Fabrice de Chaumont
 */
@IcyPluginName("Track Manager")
public class TrackManager extends PluginActionable implements AdjustmentListener, ActionListener, SwimmingPoolListener, IcyFrameListener {
    private static final Logger LOGGER = Logger.getLogger(TrackManager.class.getName());

    /**
     * is this track manager not used anymore (TODO:Should change to a listener structure.)
     */
    boolean trackManagerDestroyed = false;

    public boolean isTrackManagerDestroyed() {
        return trackManagerDestroyed;
    }

    private class MenuItemActionListener implements ActionListener {
        Class<? extends PluginTrackManagerProcessor> pluginTrackManagerProcessor;
        PluginDescriptor pluginDescriptor;

        @Contract(pure = true)
        public MenuItemActionListener(final PluginDescriptor pluginDescriptor,
                                      final Class<? extends PluginTrackManagerProcessor> pluginTrackEditorProcessor) {
            this.pluginTrackManagerProcessor = pluginTrackEditorProcessor;
            this.pluginDescriptor = pluginDescriptor;
        }

        @Override
        public void actionPerformed(final ActionEvent event) {
            loadTrackProcessor(pluginDescriptor);
        }

    }

    void loadTrackProcessor(final PluginDescriptor pluginDescriptor) {
        if (pluginDescriptor == null || pluginDescriptor.getPluginClass() == null) {
            if (LOGGER.isLoggable(Level.SEVERE))
                LOGGER.log(Level.SEVERE, "Can't load processor (plugin not found)");
            return;
        }

        PluginTrackManagerProcessor ptep = null;
        try {
            ptep = (PluginTrackManagerProcessor) pluginDescriptor.getPluginClass().getDeclaredConstructor().newInstance();
        }
        catch (final Exception e) {
            if (LOGGER.isLoggable(Level.SEVERE))
                LOGGER.log(Level.SEVERE, "Can't instantiate plugin class " + pluginDescriptor.getPluginClass(), e);
        }

        if (ptep == null) {
            if (LOGGER.isLoggable(Level.SEVERE))
                LOGGER.log(Level.SEVERE, "Can't load processor.");
            return;
        }

        ptep.setTrackPool(trackPool);
        trackPool.addTrackProcessor(ptep);

        // rebuild trackList
        rebuildTrackProcessorDisplay();
    }

    public TrackPool getTrackPool() {
        return trackPool;
    }

    void rebuildTrackProcessorDisplay() {
        rightContentPanel.removeAll();

        for (final PluginTrackManagerProcessor trackProcessor : trackPool.getTrackManagerProcessorList()) {
            rightContentPanel.add(trackProcessor.getPanel());
        }

        rightContentPanel.revalidate();

        rightContentPanel.getParent().revalidate();
    }

    JButton addTrackProcessorButton = new JButton("add Track Processor...");

    JPanel deetPanel = new JPanel();
    JMenuItem deleteLinkMenuItem = new JMenuItem("Delete links on selected tracks");
    JMenuItem deleteSelectionMenuItem = new JMenuItem("Delete Selection");

    String node = "plugins/fab/trackmanager/displaytrackpanel";
    Preferences preferences = Preferences.userRoot().node(node);
    boolean displayTrackManagerBoolean = Boolean.parseBoolean(preferences.get("displaytrackpanel", "1"));
    /*
     * String node = "plugins/fab/trackmanager/displaytrackpanel";
     * Preferences preferences = Preferences.userRoot().node( node );
     * preferences.put( "path", chooser.getCurrentDirectory().getAbsolutePath() );
     */
    JCheckBoxMenuItem displayTrackPanelCheckBoxMenuItem = new JCheckBoxMenuItem("Display Track Panel", displayTrackManagerBoolean);

    /*
     * void
     * {
     * String node = "plugins/fab/trackmanager/browser";
     *
     * Preferences preferences = Preferences.userRoot().node( node );
     * String path = preferences.get( "path" , "" );
     * chooser.setCurrentDirectory( new File(path) );
     *
     * int returnVal = chooser.showOpenDialog(null);
     * if(returnVal != JFileChooser.APPROVE_OPTION) return;
     *
     * preferences.put( "path", chooser.getCurrentDirectory().getAbsolutePath() );
     * }
     */

    JMenuItem fuseTracksMenuItem = new JMenuItem("Fuse All track-segments");
    JMenuItem selectTrackByLengthMenuItem = new JMenuItem("Select track by length...");
    JMenuItem invertSelectionMenuItem = new JMenuItem("Invert Selection");
    JMenuItem debugMenuItem = new JMenuItem("Debug");
    JPanel leftPanel;

    JMenuItem loadMenuItem = new JMenuItem("Load...");
    JMenuItem loadFromTrackMateMenuItem = new JMenuItem("Import from TrackMate...");

    JPanel mainPanel = GuiUtil.generatePanel();

    IcyFrame mainFrame = GuiUtil.generateTitleFrame("TrackManager", mainPanel, new Dimension(400, 0), true, true, true, true);
    JMenuBar menuBar = new JMenuBar();

    JPopupMenu pluginPopupMenu = new JPopupMenu("Add plugin to queue");
    JMenuItem reOrganizeMenuItem = new JMenuItem("Organize track by parent/child");
    JPanel rightContentPanel;

    JPanel rightPanel;
    JMenuItem saveMenuItem = new JMenuItem("Save as...");
    JScrollPane scrollPane;
    JMenuItem selectAllMenuItem = new JMenuItem("Select all tracks");
    JCheckBoxMenuItem externaliseCheckBoxMenuItem = new JCheckBoxMenuItem("Externalize window", false);
    JButton TestButton;
    TrackPanel trackPanel;
    TrackPool trackPool;

    JMenuItem unselectAllMenuItem = new JMenuItem("Unselect all tracks");
    JButton zoomLessButton = new JButton("Zoom -");

    JButton zoomMoreButton = new JButton("Zoom +");
    // JButton zoomYLessButton = new JButton("Zoom Y -");
    // JButton zoomYMoreButton = new JButton("Zoom Y +");

    JToggleButton showHideTrackPoolSourcesPanel = new JToggleButton("Display TrackPool Sources", true);
    // JPanel trackGroupSourceInnerPanel = GuiUtil.generatePanel("Track pool sources.");

    PopupPanel trackGroupSourceInnerPanel = new PopupPanel("Track pool sources");
    // GuiUtil.generatePanel("Track pool sources.");

    SequenceChooser sequenceSelector = new SequenceChooser();
    JMenu trackingGroupSourceMenu;
    JMenuItem trackingGroupSourceHelpMenuItem;

    JMenuItem onlineHelpMenuItem;

    public TrackManager() {
        mainFrame.addFrameListener(this);

        trackPool = new TrackPool();
        trackPool.trackManager = this;
        trackPanel = new TrackPanel(trackPool, displayTrackPanelCheckBoxMenuItem.isSelected());
        // trackPanel.setEnableLeftPanelTrackDisplay( displayTrackPanelCheckBoxMenuItem.isSelected()
        // );

        trackPanel.setPreferredSize(new Dimension(1000, 1000));

        trackPanel.setLayout(null);

        scrollPane = new JScrollPane(trackPanel, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
        scrollPane.setPreferredSize(new Dimension(800, 300));
        scrollPane.setBorder(new TitledBorder("Track View"));
        scrollPane.getVerticalScrollBar().addAdjustmentListener(this);

        final JPanel optionTrackPanel = new JPanel();
        optionTrackPanel.setLayout(new GridLayout(1, 4));

        final JMenu fileMenu = new JMenu("File");

        fileMenu.add(loadMenuItem);
        fileMenu.add(saveMenuItem);
        fileMenu.addSeparator();
        fileMenu.add(loadFromTrackMateMenuItem);

        loadMenuItem.addActionListener(this);
        saveMenuItem.addActionListener(this);
        loadFromTrackMateMenuItem.addActionListener(this);

        final JMenu editMenu = new JMenu("Edit");

        editMenu.add(selectAllMenuItem);
        editMenu.add(unselectAllMenuItem);
        editMenu.add(invertSelectionMenuItem);
        editMenu.add(selectTrackByLengthMenuItem);
        editMenu.addSeparator();
        editMenu.add(reOrganizeMenuItem);
        editMenu.add(deleteSelectionMenuItem);
        editMenu.add(deleteLinkMenuItem);
        editMenu.add(fuseTracksMenuItem);

        editMenu.addSeparator();
        // editMenu.add(debugMenuItem);
        debugMenuItem.addActionListener(this);

        selectTrackByLengthMenuItem.addActionListener(this);
        invertSelectionMenuItem.addActionListener(this);
        unselectAllMenuItem.addActionListener(this);
        reOrganizeMenuItem.addActionListener(this);
        selectAllMenuItem.addActionListener(this);
        deleteSelectionMenuItem.addActionListener(this);
        deleteLinkMenuItem.addActionListener(this);
        fuseTracksMenuItem.addActionListener(this);
        // clearTrackPoolMenuItem.addActionListener(this);
        externaliseCheckBoxMenuItem.addActionListener(this);

        final JMenu displayMenu = new JMenu("Display");
        displayMenu.add(displayTrackPanelCheckBoxMenuItem);

        displayMenu.add(externaliseCheckBoxMenuItem);

        displayTrackPanelCheckBoxMenuItem.addActionListener(this);
        // displayTrackPanelCheckBoxMenuItem.setEnabled( false );

        trackingGroupSourceMenu = new JMenu("Tracking groups");

        trackingGroupSourceHelpMenuItem = new JMenuItem("No track group available: use either file/load, a tracker or a benchmark generator to generate tracks.");
        trackingGroupSourceMenu.add(trackingGroupSourceHelpMenuItem);
        trackingGroupSourceHelpMenuItem.setEnabled(false);

        final JMenu helpMenu = new JMenu("Help");
        onlineHelpMenuItem = new JMenuItem("Help...");
        helpMenu.add(onlineHelpMenuItem);
        onlineHelpMenuItem.addActionListener(this);

        menuBar.add(fileMenu);
        menuBar.add(editMenu);
        menuBar.add(trackingGroupSourceMenu);
        menuBar.add(displayMenu);
        menuBar.add(helpMenu);

        mainFrame.setJMenuBar(menuBar);

        ComponentUtil.setFixedWidth(zoomLessButton, 150);
        ComponentUtil.setFixedWidth(zoomMoreButton, 150);
        optionTrackPanel.add(zoomLessButton);
        optionTrackPanel.add(zoomMoreButton);
        // optionTrackPanel.add(zoomYLessButton);
        // optionTrackPanel.add(zoomYMoreButton);
        zoomLessButton.addActionListener(this);
        zoomMoreButton.addActionListener(this);
        // zoomYLessButton.addActionListener(this);
        // zoomYMoreButton.addActionListener(this);

        final JPanel trackPoolSourcePanel = GuiUtil.generatePanelWithoutBorder();

        trackPoolSourcePanel.add(GuiUtil.besidesPanel(new JLabel("Display results on sequence:"), sequenceSelector));

        // trackPoolSourcePanel.add(GuiUtil.besidesPanel(showHideTrackPoolSourcesPanel));
        // trackPoolSourcePanel.add(GuiUtil.besidesPanel(trackGroupSourceInnerPanel));
        // trackPoolSourcePanel.add( trackGroupSourceInnerPanel ) ;

        final JPanel tpp = GuiUtil.generatePanel();
        showHideTrackPoolSourcesPanel.addActionListener(this);

        tpp.add(GuiUtil.besidesPanel(trackPoolSourcePanel));
        tpp.add(GuiUtil.besidesPanel(addTrackProcessorButton));

        addTrackProcessorButton.addActionListener(this);

        // mainFrame.getContentPane().setLayout(new GridLayout(1, 2));

        leftPanel = new JPanel(new BorderLayout());
        final JPanel southPanel = new JPanel(new BorderLayout());

        deetPanel.setSize(400, 200);
        deetPanel.add(new JLabel("-----------------"));

        southPanel.add(deetPanel, BorderLayout.NORTH);

        final JPanel northPanel = new JPanel();
        northPanel.setLayout(new BorderLayout());
        northPanel.add(scrollPane, BorderLayout.CENTER);
        northPanel.add(optionTrackPanel, BorderLayout.SOUTH);

        leftPanel.add(northPanel, BorderLayout.CENTER);
        // leftPanel.add(southPanel, BorderLayout.SOUTH);

        rightContentPanel = new JPanel();

        // rightContentPanel.setLayout(new FlowLayout());
        rightContentPanel.setLayout(new BoxLayout(rightContentPanel, BoxLayout.PAGE_AXIS));
        // rightContentPanel.setLayout( new BorderLayout() );

        final JPanel intermediatePanel = new JPanel(new BorderLayout());
        intermediatePanel.add(rightContentPanel, BorderLayout.NORTH);
        intermediatePanel.add(Box.createGlue(), BorderLayout.CENTER);

        final JScrollPane rightScrollPanel = new JScrollPane(intermediatePanel, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
        // JScrollPane rightScrollPanel = new JScrollPane(rightContentPanel,
        // JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
        // JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);

        rightScrollPanel.setBorder(new TitledBorder("Track Processor Rack"));

        rightPanel = new JPanel();
        rightPanel.setLayout(new BorderLayout());
        rightPanel.add(tpp, BorderLayout.NORTH);
        rightPanel.add(rightScrollPanel, BorderLayout.CENTER);

        rightPanel.validate();

        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.LINE_AXIS));

        final JSplitPane mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);

        mainPanel.add(mainSplitPane);

        mainPanel.setPreferredSize(new Dimension(800, 400));

        trackPool.fireTrackEditorProcessorChange();

        buildPluginMenu();

        mainFrame.pack();
        mainFrame.setVisible(true);
        mainFrame.addToDesktopPane();
        mainFrame.center();

        initTrackPoolInnerPanel();

        Icy.getMainInterface().getSwimmingPool().addListener(this);

        mainFrame.requestFocus();
        sequenceSelector.addActionListener(this);

        // add the trackColor Processor
        final PluginDescriptor pd = ExtensionLoader.getPlugin("plugins.fab.trackmanager.processors.TrackProcessorColorTrack");
        loadTrackProcessor(pd);
    }

    /**
     * Monitor swimming pool change event and change the track view. If a trackgroup is used in
     * another trackeditor, the trackgroup checkbox is disabled.
     */
    @Override
    public void swimmingPoolChangeEvent(final @NotNull SwimmingPoolEvent swimmingPoolEvent) {
        if (swimmingPoolEvent.getType() == SwimmingPoolEventType.ELEMENT_ADDED) {
            // System.out.println("TrackManager : swimming pool element added.");
            // System.out.println("object: " +
            // swimmingPoolEvent.getResult().getObject().getClass());

            if (swimmingPoolEvent.getResult().getObject() instanceof TrackGroup) {
                final CheckBoxResultSource checkBoxResultSource = new CheckBoxResultSource(swimmingPoolEvent.getResult(),
                        true);
                trackingGroupSourceMenu.add(checkBoxResultSource);
                // trackGroupSourceInnerPanel.add(checkBoxResultSource);
                trackingGroupSourceHelpMenuItem.setVisible(false);
            }
        }
        if (swimmingPoolEvent.getType() == SwimmingPoolEventType.ELEMENT_REMOVED) {
            trackPool.removeResult(swimmingPoolEvent.getResult());
            // resultList.remove( swimmingPoolEvent.getResult() );

            // If there is only one item to remove, no item will be left, then display help message.
            if (trackingGroupSourceMenu.getItemCount() == 2) {
                trackingGroupSourceHelpMenuItem.setVisible(true);
            }

            for (int i = 0; i < trackingGroupSourceMenu.getItemCount(); i++) {
                final JMenuItem mi = trackingGroupSourceMenu.getItem(i);
                if (mi instanceof final CheckBoxResultSource cbrs) {
                    if (cbrs.result == swimmingPoolEvent.getResult()) {
                        cbrs.destroy();
                        return;
                    }
                }
            }

            // Component[] componentList = trackGroupSourceInnerPanel.getComponents();
            // // marchera pas car les composants en question sont des panels..
            // for (int index = 0; index < componentList.length; index++)
            // {
            // Component component = componentList[index];
            // if (component instanceof CheckBoxResultSource)
            // {
            // CheckBoxResultSource checkBoxResultSource = (CheckBoxResultSource) component;
            // if (checkBoxResultSource.result == swimmingPoolEvent.getResult())
            // {
            // checkBoxResultSource.destroy();
            //
            // }
            // }
            // }

        }
        // trackGroupSourceInnerPanel.updateUI();
    }

    private void initTrackPoolInnerPanel() {
        // trackingGroupSourceMenu.removeAll();

        // trackGroupSourceInnerPanel.removeAll();

        final ArrayList<SwimmingObject> swimmingPoolArrayList = Icy.getMainInterface().getSwimmingPool().getObjects();

        for (final SwimmingObject result : swimmingPoolArrayList) {
            if (result.getObject() instanceof TrackGroup) {
                final boolean enabled = true;
                // for (TrackManager trackEditor : trackManagerList)
                // {
                // if (trackEditor.getResultList().contains(result))
                // {
                // enabled = false;
                // break;
                // }
                // }

                final CheckBoxResultSource checkBoxResultResource = new CheckBoxResultSource( // "" +
                        // result.getObject().toString(),
                        result, enabled);

                trackingGroupSourceMenu.add(checkBoxResultResource);
                // trackGroupSourceInnerPanel.add( checkBoxResultResource );

                // setEnableCheckBoxResultSource( result, true );
                // checkBoxResultResource.setSelected( true );

                trackingGroupSourceHelpMenuItem.setVisible(false);

            }
        }
    }

//    /**
//     * Called from other trackeditor to check witch results are already used.
//     */
//    private ArrayList<SwimmingObject> getResultList() {
//        return trackPool.resultList;
//    }

    // /**
    // * Called from other TrackEditor to avoid having the same track used
    // * FIXME: limitation should be removed.
    // */
    // private void setEnableCheckBoxResultSource(SwimmingObject result, boolean enabled)
    // {
    // Component[] componentList = trackGroupSourceInnerPanel.getComponents();
    // for (int index = 0; index < componentList.length; index++)
    // {
    //// System.out.println("test");
    // Component component = componentList[index];
    // System.out.println(component);
    // if (component instanceof CheckBoxResultSource)
    // {
    // // System.out.println("checking component...");
    // CheckBoxResultSource checkBoxResultSource = (CheckBoxResultSource) component;
    // if (checkBoxResultSource.result == result)
    // {
    // // System.out.println("checking to " + enabled);
    // checkBoxResultSource.setEnabled(enabled);
    // }
    // }
    // }
    // }

    //** List of sequences which will be kept synchronize while the t cursor are moving. */
    // private ArrayList<Sequence> sequenceToKeepSynchronizeInT = new ArrayList<Sequence>();

    /**
     * current time in track window
     */
    int currentT = 0;

    public int getCurrentPositionT() {
        return currentT;
    }

    public void setCurrentPositionT(final int t) {
        currentT = t;
        reSynchroTSequence();
        trackPool.fireTrackEditorProcessorChange();
    }

    void timeCursorChanged(final int t) {
        setCurrentPositionT(t);
    }

    /**
     * set the sequence with the current time set in trackEditor
     */
    private void reSynchroTSequence() {
        // System.out.println("Synchro sequence");
        // for (Sequence sequence : sequenceToKeepSynchronizeInT)
        // {
        // System.out.println("synchro on sequence : " + sequence );
        // sequence.setT(currentT);
        // }

        final Sequence sequence = sequenceSelector.getSelectedSequence();
        if (sequence != null) {
            for (final Viewer viewer : Icy.getMainInterface().getViewers(sequence)) {
                viewer.setPositionT(currentT);
            }
            // sequence.setT( currentT );
        }

    }

    class CheckBoxResultSource extends JCheckBoxMenuItem implements ActionListener {
        // JCheckBox checkBox = new JCheckBox();
        // JToggleButton trackTimeOnSequenceButton = new JToggleButton("T");
        SwimmingObject result;

        public CheckBoxResultSource(final @NotNull SwimmingObject result, final boolean enabled) {
            if (!(result.getObject() instanceof final TrackGroup trackGroup)) {
                if (LOGGER.isLoggable(Level.SEVERE))
                    LOGGER.log(Level.SEVERE, "Result object type not supported.");
                return;
            }

            final int nbTracks = trackGroup.getTrackSegmentList().size();

            final String label = result.getObject().toString() + " (" + nbTracks + " Tracks)";

            // trackTimeOnSequenceButton.setToolTipText("Keep Time synchro with sequence");
            // trackTimeOnSequenceButton.setSelected( true ) ;
            // ComponentUtil.setFixedWidth( trackTimeOnSequenceButton , 30 );
            // trackTimeOnSequenceButton.addActionListener(this);
            // trackTimeOnSequenceButton.setPreferredSize(new Dimension(20, 15));
            this.setText(label);
            // checkBox.setText(label);
            // this.setLayout( new BoxLayout( this , BoxLayout.PAGE_AXIS ) );
            // this.add( GuiUtil.createLineBoxPanel( checkBox , Box.createHorizontalGlue() ) );
            // //BorderLayout.WEST);
            this.result = result;
            // if (result.getObject() instanceof TrackGroup)
            // sequence = ((TrackGroup) result.getObject()).getSequence();
            // else
            // sequence = null;
            setEnabled(enabled);
            this.addActionListener(this);
            this.setSelected(true);
            // checkBox.addActionListener(this);
            // checkBox.setSelected( true );

            // -------- This code enables the result and check if other trackmanager uses it.
            trackPool.addResult(result);
            trackPool.fireTrackEditorProcessorChange();
            // FIXME : should remove the limitation of the other trackManager
            // for (TrackManager trackEditor : trackManagerList)
            // {
            // if (trackEditor != TrackManager.this)
            // {
            // trackEditor.setEnableCheckBoxResultSource(result, false);
            // }
            // }
            // ----------
        }

        // public void setSelected(boolean b) {
        //
        // checkBox.setSelected( b );
        // }

        public void destroy() {
            // remove the sequence tracked
            // if (trackTimeOnSequenceButton.isSelected())
            // {
            // sequenceToKeepSynchronizeInT.remove(sequence);
            // }
            trackingGroupSourceMenu.remove(this);
            // trackGroupSourceInnerPanel.remove(this);
            trackPool.removeResult(result);
            trackPool.fireTrackEditorProcessorChange();
        }

        @Override
        public void actionPerformed(final @NotNull ActionEvent e) {

            // if (e.getSource() == trackTimeOnSequenceButton)
            // {
            // if (trackTimeOnSequenceButton.isSelected())
            // {
            // if (sequence != null)
            // sequenceToKeepSynchronizeInT.add(sequence);
            // }
            // else
            // {
            // sequenceToKeepSynchronizeInT.remove(sequence);
            // }
            // reSynchroTSequence();
            // }

            if (e.getSource() == this) {
                if (this.isSelected()) {
                    // resultList.add( result );
                    trackPool.addResult(result);
                    // trackPool.getDisplaySequence().painterChanged( null );
                    // for (TrackManager trackEditor : trackManagerList)
                    // {
                    // // System.out.println("track editor passs");
                    // if (trackEditor != TrackManager.this)
                    // {
                    // // System.out.println("call ok");
                    // trackEditor.setEnableCheckBoxResultSource(result, false);
                    // }
                    // }

                }
                else {
                    trackPool.removeResult(result);
                    // trackPool.getDisplaySequence().painterChanged( null );
                    // for (TrackManager trackEditor : trackManagerList)
                    // {
                    // if (trackEditor != TrackManager.this)
                    // trackEditor.setEnableCheckBoxResultSource(result, true);
                    // }

                }
                trackPool.fireTrackEditorProcessorChange();
            }
        }

        // @Override
        // public boolean isEnabled()
        // {
        // if (checkBox == null)
        // return false;
        // return checkBox.isEnabled();
        // }

        // @Override
        // public void setEnabled(boolean enabled)
        // {
        // checkBox.setEnabled(enabled);
        // }

    }

    private void loadTracks() {
        final JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select track.xml file.");

        chooser.setFileFilter(new FileNameExtensionFilter("XML track file", "xml"));

        final String node = "plugins/fab/trackmanager/browser";

        final Preferences preferences = Preferences.userRoot().node(node);
        final String path = preferences.get("path", "");
        chooser.setCurrentDirectory(new File(path));

        final int returnVal = chooser.showOpenDialog(null);
        if (returnVal != JFileChooser.APPROVE_OPTION)
            return;

        preferences.put("path", chooser.getCurrentDirectory().getAbsolutePath());

        try {
            final List<TrackGroup> groups = loadTracks(chooser.getSelectedFile().getAbsolutePath());

            // add group to swimming pool
            for (final TrackGroup group : groups) {
                final SwimmingObject so = new SwimmingObject(group);
                Icy.getMainInterface().getSwimmingPool().add(so);
            }

            trackPool.fireTrackEditorProcessorChange();
        }
        catch (final IllegalArgumentException ex) {
            MessageDialog.showDialog(ex.getMessage(), JOptionPane.ERROR_MESSAGE);
        }
    }

    public static @NotNull List<TrackGroup> loadTracks(final String path) throws IllegalArgumentException {
        final Document document = XMLUtil.loadDocument(path, false);
        return loadTracks(document);
    }

    @Contract("null -> fail")
    public static @NotNull List<TrackGroup> loadTracks(final Document document) throws IllegalArgumentException {
        if (document == null)
            throw new IllegalArgumentException("Invalid track file !");

        final Element rootElement = document.getDocumentElement();

        if (XMLUtil.getElement(rootElement, "trackfile") == null)
            throw new IllegalArgumentException("Invalid track file: the tag trackfile is missing.");

        final List<TrackGroup> result = new ArrayList<>();

        for (final Element trackGroupElement : XMLUtil.getElements(rootElement, "trackgroup")) {
            // create track group
            final TrackGroup trackGroup = new TrackGroup(null);
            trackGroup.setDescription(XMLUtil.getAttributeValue(trackGroupElement, "description", "no description"));

            for (final Element trackSegmentElement : XMLUtil.getElements(trackGroupElement)) {
                // create track segment
                final TrackSegment trackSegment = new TrackSegment();
                final int id = XMLUtil.getAttributeIntValue(trackSegmentElement, "id", -1);

                if (id == -1)
                    trackSegment.generateId();
                else
                    trackSegment.setId(id);

                for (final Element detectionElement : XMLUtil.getElements(trackSegmentElement)) {
                    // create detection
                    final Detection detection = Detection.createDetection(XMLUtil.getAttributeValue(detectionElement,
                            "classname", "plugins.nchenouard.spot.Detection"));

                    // load it
                    detection.loadFromXML(detectionElement);
                    // add to track segement
                    trackSegment.addDetection(detection);
                }

                // add track segment to track group
                trackGroup.addTrackSegment(trackSegment);
            }

            // add track group to result
            result.add(trackGroup);
        }

        // get links
        for (final Element linkListElement : XMLUtil.getElements(rootElement, "linklist")) {
            for (final Element link : XMLUtil.getElements(linkListElement)) {
                final int idFrom = XMLUtil.getAttributeIntValue(link, "from", -1);
                final int idTo = XMLUtil.getAttributeIntValue(link, "to", -1);

                final TrackSegment from = TrackSegment.getTrackSegmentById(idFrom);
                final TrackSegment to = TrackSegment.getTrackSegmentById(idTo);

                if (from != null && to != null)
                    TrackPool.createLink(result, from, to);
            }
        }

        return result;
    }

    private void loadTracksFromTrackMate() {
        final JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Import TrackMate xml track file.");

        chooser.setFileFilter(new FileNameExtensionFilter("TrackMate XML track file", "xml"));

        final String node = "plugins/fab/trackmanager/browser";

        final Preferences preferences = Preferences.userRoot().node(node);
        final String path = preferences.get("path", "");
        chooser.setCurrentDirectory(new File(path));

        final int returnVal = chooser.showOpenDialog(null);
        if (returnVal != JFileChooser.APPROVE_OPTION)
            return;

        preferences.put("path", chooser.getCurrentDirectory().getAbsolutePath());

        try {
            final List<TrackGroup> groups = loadTracksFromTrackMate(chooser.getSelectedFile().getAbsolutePath());

            // add group to swimming pool
            for (final TrackGroup group : groups) {
                final SwimmingObject so = new SwimmingObject(group);
                Icy.getMainInterface().getSwimmingPool().add(so);
            }

            trackPool.fireTrackEditorProcessorChange();
        }
        catch (final IllegalArgumentException ex) {
            MessageDialog.showDialog(ex.getMessage(), JOptionPane.ERROR_MESSAGE);
        }
    }

    public static @NotNull List<TrackGroup> loadTracksFromTrackMate(final String path) {
        final Document document = XMLUtil.loadDocument(path, false);
        return loadTracksFromTrackMate(document);
    }

    @Contract("null -> fail")
    public static @NotNull List<TrackGroup> loadTracksFromTrackMate(final Document document) throws IllegalArgumentException {
        if (document == null)
            throw new IllegalArgumentException("Invalid track file !");

        final Element trackNode = XMLUtil.getElement(document.getDocumentElement().getParentNode(), "Tracks");
        if (trackNode == null)
            throw new IllegalArgumentException("Unsupported version of TrackMate XML file !\nTry to use the Icy XML export in TrackMate instead and directly load it with 'TrackManager-->File->Load...' command.");

        final List<TrackGroup> result = new ArrayList<>();
        //final String unit = XMLUtil.getAttributeValue(trackNode, "spaceUnits", "pixel");

        //double unitMultiplicator = 1;
        //if (unit.equals("micron"))
        //    unitMultiplicator = 0.000001d;

        for (final Element trackElement : XMLUtil.getElements(document.getDocumentElement().getParentNode(), "Tracks")) {
            // create track group
            final TrackGroup trackGroup = new TrackGroup(null);
            trackGroup.setDescription(XMLUtil.getAttributeValue(trackElement, "description", "no description"));

            for (final Element trackSegmentElement : XMLUtil.getElements(trackElement)) {
                // create track segment
                final TrackSegment trackSegment = new TrackSegment();

                final int id = XMLUtil.getAttributeIntValue(trackSegmentElement, "id", -1);
                if (id == -1)
                    trackSegment.generateId();
                else
                    trackSegment.setId(id);

                for (final Element detectionElement : XMLUtil.getElements(trackSegmentElement)) {
                    // create detection
                    final Detection detection = Detection.createDetection("plugins.nchenouard.spot.Detection");

                    // load it
                    detection.loadFromXML(detectionElement);
                    // add to track segment
                    trackSegment.addDetection(detection);
                }

                // add track segment to group
                trackGroup.addTrackSegment(trackSegment);
            }

            // add track group to result
            result.add(trackGroup);
        }

        return result;
    }

    private void debug() {
        if (LOGGER.isLoggable(Level.WARNING))
            LOGGER.log(Level.WARNING, "--- debug");
        // for ( Link link : trackPool.getLinks() )
        // {
        // System.out.println( link );
        // }

        // System.out.println( "Track segment idHashMapSize = " + TrackSegment.idHashMapList.size()
        // );
        // TrackSegment.idHashMapList.clear();
    }

    private void saveTracks() {
        final JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select track.xml file.");
        chooser.setFileFilter(new FileNameExtensionFilter("XML track file", "xml"));

        final String node = "plugins/fab/trackmanager/browser";

        final Preferences preferences = Preferences.userRoot().node(node);
        final String path = preferences.get("path", "");
        chooser.setCurrentDirectory(new File(path));

        final int returnVal = chooser.showSaveDialog(null);
        if (returnVal != JFileChooser.APPROVE_OPTION)
            return;

        preferences.put("path", chooser.getCurrentDirectory().getAbsolutePath());

        saveTracks(chooser.getSelectedFile().getAbsolutePath(), trackPool.getTrackGroupList());
    }

    public static void saveTracks(final String path, final List<TrackGroup> groups) {
        // get file
        final File file = new File(FileUtil.setExtension(path, ".xml"));
        // generate document
        final Document document = saveTracks(groups);
        // save it
        XMLUtil.saveDocument(document, file);
    }

    public static @NotNull Document saveTracks(final @NotNull List<TrackGroup> groups) {
        final Document result = XMLUtil.createDocument(true);
        final Element rootElement = result.getDocumentElement();

        final Element versionElement = XMLUtil.addElement(rootElement, "trackfile");
        versionElement.setAttribute("version", "1");

        for (final TrackGroup trackGroup : groups) {
            final Element trackGroupElement = XMLUtil.addElement(rootElement, "trackgroup");
            trackGroupElement.setAttribute("description", trackGroup.getDescription());

            for (final TrackSegment trackSegment : trackGroup.getTrackSegmentList()) {
                final Element trackElement = XMLUtil.addElement(trackGroupElement, "track");
                trackElement.setAttribute("id", String.valueOf(trackSegment.getId()));

                for (final Detection detection : trackSegment.getDetectionList()) {
                    final Element detectionElement = XMLUtil.addElement(trackElement, "detection");

                    detectionElement.setAttribute("classname", detection.getClass().getName());
                    // detectionElement.setAttribute( "x", ""+detection.x );
                    // detectionElement.setAttribute( "y", ""+detection.y );
                    // detectionElement.setAttribute( "z", ""+detection.z );
                    // detectionElement.setAttribute( "t", ""+detection.t );
                    detection.saveToXML(detectionElement);
                }
            }
        }

        // save links
        final Element linklistElement = XMLUtil.addElement(rootElement, "linklist");
        for (final Link link : TrackPool.getLinks(groups)) {
            final Element trackElement = XMLUtil.addElement(linklistElement, "link");

            trackElement.setAttribute("from", String.valueOf(link.getStartSegment().getId()));
            trackElement.setAttribute("to", String.valueOf(link.getEndSegment().getId()));
        }

        return result;
    }

    /**
     * Attempt to re-classify / re-organize the track order to improve track understandment.
     */
    public void reOrganize() {
        for (final TrackGroup trackGroup : trackPool.getTrackGroupList()) {
            @SuppressWarnings("unchecked")
            final List<TrackSegment> trackSegmentList = (ArrayList<TrackSegment>) trackGroup.getTrackSegmentList().clone();
            @SuppressWarnings("unchecked")
            final List<TrackSegment> trackSegmentListOrganized = (ArrayList<TrackSegment>) trackGroup.getTrackSegmentList().clone();

            final int tMax = trackPool.getLastDetectionTimePoint();

            for (int t = 0; t < tMax; t++)
                for (final TrackSegment ts : trackSegmentList) {
                    if (ts.getFirstDetection().getT() == t) {
                        for (int index = 0; index < ts.nextList.size(); index++) {
                            final TrackSegment ts2 = ts.nextList.get(index);
                            trackSegmentListOrganized.remove(ts2);
                            int targetIndex = trackSegmentListOrganized.indexOf(ts) - ts.nextList.size() / 2 + index
                                    + 1;
                            if (targetIndex < 0)
                                targetIndex = 0;
                            trackSegmentListOrganized.add(targetIndex, ts2);
                        }
                    }
                }

            trackGroup.getTrackSegmentList().clear();
            trackGroup.getTrackSegmentList().addAll(trackSegmentListOrganized);

        }
        trackPool.fireTrackEditorProcessorChange();

        new AnnounceFrame("Track reorganized by parents / childs", 2);

    }

    @Override
    public void actionPerformed(final @NotNull ActionEvent e) {
        if (e.getSource() == onlineHelpMenuItem) {
            NetworkUtil.openBrowser("https://icy.bioimageanalysis.org/plugin/track-manager/");
        }

        if (e.getSource() == loadMenuItem) {
            loadTracks();
        }

        if (e.getSource() == loadFromTrackMateMenuItem) {
            loadTracksFromTrackMate();
        }

        if (e.getSource() == saveMenuItem) {
            saveTracks();
        }

        if (e.getSource() == sequenceSelector) {
            trackPool.setDisplaySequence(sequenceSelector.getSelectedSequence());
        }

        if (e.getSource() == reOrganizeMenuItem) {
            reOrganize();
        }

        if (e.getSource() == displayTrackPanelCheckBoxMenuItem) {
            trackPanel.setEnableLeftPanelTrackDisplay(displayTrackPanelCheckBoxMenuItem.isSelected());

            final String node = "plugins/fab/trackmanager/displaytrackpanel";
            final Preferences preferences = Preferences.userRoot().node(node);

            preferences.putBoolean("displaytrackpanel", displayTrackPanelCheckBoxMenuItem.isSelected());

            // preferences.put( "displaytrackpanel", displayTrackPanelCheckBoxMenuItem.isSelected()
            // );
            // boolean displayTrackManagerBoolean = Boolean.parseBoolean( preferences.get(
            // "displaytrackpanel" , "1" ) );
            // trackPool.fireTrackEditorProcessorChange();
        }

        if (e.getSource() == externaliseCheckBoxMenuItem) {
            if (externaliseCheckBoxMenuItem.isSelected()) {
                mainFrame.externalize();
            }
            else {
                mainFrame.internalize();
            }
        }

        if (e.getSource() == addTrackProcessorButton) {
            pluginPopupMenu.show(addTrackProcessorButton, 0, 0);
        }

        if (e.getSource() == showHideTrackPoolSourcesPanel) {
            trackGroupSourceInnerPanel.setVisible(showHideTrackPoolSourcesPanel.isSelected());
        }

        if (e.getSource() == selectAllMenuItem) {
            trackPool.selectAllTracks();
        }

        if (e.getSource() == unselectAllMenuItem) {
            trackPool.unselectAllTracks();
        }

        //if (e.getSource() == selectTrackByLengthMenuItem) {
        //TrackLengthDialog tld = new TrackLengthDialog(trackPool);

        // TrackLengthDialog tld = new TrackLengthDialog();
        // JDialog dialog = new JDialog();
        // JPanel panel = new JPanel();
        // panel.setLayout( new FlowLayout(FlowLayout.LEFT) );
        // panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 0));
        // JTextField textfield = new JTextField(8);
        // textfield.setBounds(10, 10, 40, 20);
        // panel.add(textfield);
        // dialog.add(panel);
        // dialog.setSize(200, 100);
        // dialog.setLocationRelativeTo(null);
        // dialog.setVisible(true);
        //}

        if (e.getSource() == invertSelectionMenuItem) {
            trackPool.invertSelection();
        }

        if (e.getSource() == deleteLinkMenuItem) {
            trackPool.deleteSelectedLink();
        }

        if (e.getSource() == deleteSelectionMenuItem) {
            trackPool.deleteSelectedTracks();
        }

        if (e.getSource() == debugMenuItem) {
            debug();
        }

        if (e.getSource() == fuseTracksMenuItem) {
            trackPool.fuseAllTracks();
        }

        if (e.getSource() == zoomLessButton) {
            int newX = (int) trackPanel.getDetectDim().getWidth() - 5;
            if (newX <= 0)
                newX = 5;
            trackPanel.setDetectDim(new Dimension(newX, newX)); // TODO check if 'newX' should be used as 'Y' argument
            trackPool.fireTrackEditorProcessorChange();
        }
        if (e.getSource() == zoomMoreButton) {
            trackPanel.setDetectDim(new Dimension((int) trackPanel.getDetectDim().getWidth() + 5, (int) trackPanel.getDetectDim().getHeight() + 5));
            trackPool.fireTrackEditorProcessorChange();
        }
        // if (e.getSource() == zoomYLessButton)
        // {
        // int newY = (int) trackPanel.getDetectDim().getHeight() - 5;
        // if (newY <= 0)
        // newY = 5;
        // trackPanel.setDetectDim(new Dimension((int) trackPanel.getDetectDim().getWidth(), newY));
        // trackPool.fireTrackEditorProcessorChange();
        // }
        // if (e.getSource() == zoomYMoreButton)
        // {
        // trackPanel.setDetectDim(new Dimension((int) trackPanel.getDetectDim().getWidth(), (int)
        // trackPanel
        // .getDetectDim().getHeight() + 5));
        // trackPool.fireTrackEditorProcessorChange();
        // }
    }

    @Override
    public void adjustmentValueChanged(final AdjustmentEvent arg0) {

    }

    private void buildPluginMenu() {
        pluginPopupMenu.removeAll();
        {
            final JMenuItem menuItem = new JMenuItem("Add Track Processor Plugin...");
            menuItem.setEnabled(false);
            pluginPopupMenu.add(menuItem);
            pluginPopupMenu.addSeparator();
        }

        for (final PluginDescriptor plugin : ExtensionLoader.getPlugins()) {
            if (plugin.isInstanceOf(PluginTrackManagerProcessor.class))
                if (!plugin.isAbstract()) {
                    //final JMenuItem menuItem = new JMenuItem(plugin.getName(), new ImageIcon(plugin.getIcon().getImage().getScaledInstance(24, 24, Image.SCALE_SMOOTH)));
                    final IcyPluginMenuItem menuItem = new IcyPluginMenuItem(plugin);

                    pluginPopupMenu.add(menuItem);

                    menuItem.addActionListener(new MenuItemActionListener(plugin, PluginTrackManagerProcessor.class));
                }
        }
    }

    public void moveTrackProcessor(final PluginTrackManagerProcessor trackEditorProcessor, final int i) {
        int newIndex = trackPool.getTrackManagerProcessorList().indexOf(trackEditorProcessor);
        // System.out.println("previous index: " + newIndex );
        newIndex += i;
        // System.out.println("previous index+offset: " + newIndex );
        if (newIndex < 0)
            newIndex = 0;
        if (newIndex > trackPool.getTrackManagerProcessorList().size() - 1)
            newIndex = trackPool.getTrackManagerProcessorList().size() - 1;

        // System.out.println("new index: " + newIndex );

        trackPool.getTrackManagerProcessorList().remove(trackEditorProcessor);
        trackPool.getTrackManagerProcessorList().add(newIndex, trackEditorProcessor);

        rebuildTrackProcessorDisplay();
        trackPool.fireTrackEditorProcessorChange();

        // System.out.println("Demande changement de place.");
    }

    @Override
    public void icyFrameActivated(final IcyFrameEvent e) {

    }

    @Override
    public void icyFrameClosed(final IcyFrameEvent e) {
        // release T synchro with sequences.
        // sequenceToKeepSynchronizeInT.clear();

        // Free all lock on tracks.
        // for (TrackManager trackEditor : trackManagerList)
        // {
        // if (trackEditor != TrackManager.this)
        // {
        // for (SwimmingObject result : trackPool.resultList)
        // {
        // trackEditor.setEnableCheckBoxResultSource(result, true);
        // }
        // }
        // }

        // close all track processors.

        @SuppressWarnings("unchecked")
        final ArrayList<PluginTrackManagerProcessor> processorList = (ArrayList<PluginTrackManagerProcessor>) trackPool.getTrackManagerProcessorList().clone();
        for (final PluginTrackManagerProcessor tep : processorList) {
            tep.Close();
        }

        // System.out.println("debug: track editor closed.");

        trackPool.removePainter();

        trackPool.trackManager = null;
        trackPool.resultList.clear();
        // trackManagerList.remove(this);

        trackManagerDestroyed = true;
    }

    @Override
    public void icyFrameClosing(final IcyFrameEvent e) {

    }

    @Override
    public void icyFrameDeactivated(final IcyFrameEvent e) {

    }

    @Override
    public void icyFrameDeiconified(final IcyFrameEvent e) {

    }

    @Override
    public void icyFrameIconified(final IcyFrameEvent e) {

    }

    @Override
    public void icyFrameOpened(final IcyFrameEvent e) {

    }

    @Override
    public void icyFrameExternalized(final IcyFrameEvent e) {

    }

    @Override
    public void icyFrameInternalized(final IcyFrameEvent e) {

    }

    // public void setDisplaySequence(Sequence sequence) {
    //
    // sequenceSelector.setSequenceSelected( sequence );
    //
    // }

    public Sequence getDisplaySequence() {
        return sequenceSelector.getSelectedSequence();
    }

    public void setDisplaySequence(final Sequence sequence) {
        sequenceSelector.setSelectedSequence(sequence);
    }

    @Override
    public void run() {
        //
    }
}
