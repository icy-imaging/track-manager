package plugins.fab.trackmanager;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import icy.gui.dialog.ActionDialog;
import icy.gui.util.GuiUtil;

import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JTextField;

public class TrackLengthDialog implements ActionListener {

	JTextField textField= new JTextField("3-10");
	TrackPool trackPool;
	
	public TrackLengthDialog( TrackPool trackPool ) {

		this.trackPool = trackPool;
		ActionDialog actionDialog = new ActionDialog("Select by length");
    	actionDialog.getContentPane().setLayout( 
    			new BoxLayout( actionDialog.getContentPane(), BoxLayout.PAGE_AXIS ) );

    	actionDialog.getContentPane().add( GuiUtil.createLineBoxPanel( new JLabel("Use the syntax: from-to (in number of frame):" ) ) );
    	actionDialog.getContentPane().add( GuiUtil.createLineBoxPanel( textField ) );
    	
    	actionDialog.pack();
    	actionDialog.setLocationRelativeTo( null );
    	
    	actionDialog.setOkAction( this );
    	actionDialog.setVisible( true );
    
		
	}

	@Override
	public void actionPerformed(ActionEvent e) {

		String text = textField.getText();

		String[] values = text.split("-");
		int lowVal = 0;
		int highVal = Integer.MAX_VALUE;
		try
		{
			lowVal = Integer.parseInt( values[0] );        		
		} catch ( Exception e1 )
		{
			lowVal = 0;
		}
		try
		{
			highVal = Integer.parseInt( values[1] );
		} catch ( Exception e1 )
		{
			highVal = Integer.MAX_VALUE;
		}

		System.out.println( "low val : " +lowVal );
		System.out.println( "high val : " +highVal );

		trackPool.selectTracksByLength( lowVal , highVal );
		
		
	}
}
