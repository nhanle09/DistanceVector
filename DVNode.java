import java.awt.Container;
import java.awt.Dimension;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class DVNode implements Runnable 
{
	// Network variables
	private final int BASE_PORT = 5680;

	// Class Distance Vector variables
	private int node_id;
	// Node's primary Distance Vector Row ( that will be send out to neighbors )
	private DVRow primary_dvr;
	// Received Distance Vector Row from other nodes
	private DVRow received_dvr;
	// Store neighboring's edge cost ( C( x, v ) values )
	private Map<Integer, Integer> edge_cost;
	// Store neighboring's edge Distance Vector Row ( Dv( y ) values )
	private Map<Integer, DVRow> edge_dvr;

	// Store primary DVRow and Neighbor's DVRow into a vector for JTable
	private Vector<Vector<String>> dt_vector;

	// GUI Variables
	private JTextArea log_area;
	private JTable dt_table;

	// Constructor
	DVNode( int id, HashMap<Integer, DVRow> list, Boolean step ) 
	{
		// Variable definition and assignments
		node_id = id;
		edge_cost = new HashMap<Integer, Integer>();
		edge_dvr = new HashMap<Integer, DVRow>();
		dt_vector = new Vector<Vector<String>>();
		log_area = new JTextArea();

		// Initialize Primary DVR with all infinity values
		primary_dvr = new DVRow( node_id, create_dvr_map( list.size() ) );
		// Set link cost of primary node to itself to zero
		zero_self( primary_dvr, node_id );

		// Add Neighbor information into C( x, v ) and edge DVR table
		for ( Map.Entry<Integer, Integer> entry : list.get( node_id ).get_dvr().entrySet() ) 
		{
			// Add neighbor and corresponding C( x, v ) value
			edge_cost.put( entry.getKey(), entry.getValue() );
			// Add Neighbor infinity DV
			edge_dvr.put( entry.getKey(), 
				new DVRow( entry.getKey(), create_dvr_map( list.size() ) ) );
		}

		// Changed DV value of neighboring node to non-infinity values
		for ( Map.Entry<Integer, DVRow> entry : list.entrySet() ) 
		{
			if ( edge_cost.containsKey( entry.getKey() ) ) 
			{
				primary_dvr.get_dvr().put( entry.getKey(), edge_cost.get( entry.getKey() ) );
			}
		}
		
		// Prep Distance Table Vector for JTable inputs
		dt_vector.add( convert_dvr_to_vector( primary_dvr ) );
		for ( Map.Entry<Integer, DVRow> entry : edge_dvr.entrySet() ) 
		{
			dt_vector.add( convert_dvr_to_vector( entry.getValue() ) );
		}
		dt_table = new JTable( dt_vector, get_header_vector().get( 0 ) );
	}

	// Replace any entries from node to itself with zero
	public static DVRow zero_self( DVRow dt, int self_val ) 
	{
		HashMap<Integer, Integer> nbrdt = dt.get_dvr();
		nbrdt.put( self_val, 0 );
		dt.set_dvr( nbrdt );
		return dt;
	}

	// Create a distance vector row with default values set to 16 (infinity)
	public static HashMap<Integer, Integer> create_dvr_map( int size ) 
	{
		HashMap<Integer, Integer> dt = new HashMap<Integer, Integer>();
		for( int i = 1; i <= size; i++ )
		{
			dt.put( i, 16 );
		}
		return dt;
	}

	// Return a vector that contains a row of distance vector
	public static Vector<String> convert_dvr_to_vector( DVRow dv_row ) 
{
	Vector<String> dt = new Vector<String>();
	dt.add( "Node " + Integer.toString( dv_row.get_id() ) );

	for ( Map.Entry<Integer, Integer> entry : dv_row.get_dvr().entrySet() ) 
	{
		dt.add( Integer.toString( entry.getValue() ) );
	}
	return dt;
}

	// Update primary Distance Vector Row using Bellman-Ford method
	// and return true if updated distance(s) are lower than existing
	boolean main_dvr_change() 
	{
		// Iterate through all keys in primary dv
		Map<Integer, Integer> dvr_table = primary_dvr.get_dvr();
		for ( Map.Entry<Integer, Integer> dvr_entry : dvr_table.entrySet() )
		{
			// Destination ID Dest
			int dest_val = dvr_entry.getKey();
			// Candidates to select minimum values from
			ArrayList<Integer> dv_candidates = new ArrayList<Integer>();
			// Update if destination node doesn't match primary node
			if ( dvr_entry.getKey() != node_id ) 
			{
				// Iterate through each node in the neighbor to calculate C( x, v )
				for ( Map.Entry<Integer, Integer> edge_entry : edge_cost.entrySet() ) 
				{
					int edge_num = edge_entry.getValue();
					int edge_to_dest = edge_dvr.get( edge_entry.getKey() ).get_dvr().get( dest_val );
					dv_candidates.add( edge_num + edge_to_dest );
				}

				// Set the min value from the calculation as new DV if it's less than current
				int min_dvr = Collections.min( dv_candidates );
				HashMap<Integer, Integer> temp_dvr = primary_dvr.get_dvr();

				// Compare min value with current
				if ( min_dvr < primary_dvr.get_dvr().get( dest_val ) ) 
				{
					temp_dvr.put( dest_val, min_dvr );
					primary_dvr.set_dvr( temp_dvr );
					dt_vector.set( 0, convert_dvr_to_vector( primary_dvr ) );
					return true;
				}
			}
		}
		return false;
	}

	// Return a 2D vector that contains neighbor values data
	Vector<Vector<String>> get_edge_vector() 
	{
		Vector<Vector<String>> ev = new Vector<Vector<String>>();
		Vector<String> data = new Vector<String>();
		data.add( "C( " + Integer.toString( node_id ) + ", v )" );

		// Adding neighbors to DT Vector
		for ( Map.Entry<Integer, Integer> entry : edge_cost.entrySet() ) 
		{
			data.add( Integer.toString( entry.getValue() ) );
		}
		ev.add( data );
		return ev;
	}

	// Return a 2D vector that contains header for D( v, y ) and c( x, v )
	Vector<Vector<String>> get_header_vector() 
	{
		// DV Table Header Vector
		Vector<String> dt_header = new Vector<String>();
		dt_header.add( "D( v, y )" );
		for( int i = 1; i <= primary_dvr.get_dvr().size(); i++ )
		//for ( Integer i : node_set ) 
		{
			dt_header.add( "Node " + Integer.toString( i ) );
		}
		// Neighbor Table Header Vector
		Vector<String> nbr_header = new Vector<String>();
		nbr_header.add( "C( x, v )" );
		for ( Map.Entry<Integer, Integer> entry : edge_cost.entrySet() ) 
		{
			nbr_header.add( "Node " + Integer.toString( entry.getKey() ) );
		}

		Vector<Vector<String>> header_vector = new Vector<Vector<String>>();
		header_vector.add( dt_header );
		header_vector.add( nbr_header );

		return header_vector;
	}

	// Broadcast main distance vector node to all neighbor host
	void broadcast_dvr() throws UnknownHostException, IOException, InterruptedException 
	{
		for ( Map.Entry<Integer, Integer> entry : edge_cost.entrySet() ) 
		{
			send_dvr_node( BASE_PORT + entry.getKey() );
			log_area.append( "Sent updated DT to node " + entry.getKey() + ".\n" );
			TimeUnit.MILLISECONDS.sleep( 500 );
		}
	}
	
	// Process received Distance Vector Row and broadcast changes to neighboring edges
	void send_and_receive() throws UnknownHostException, IOException, InterruptedException
	{
		// Setup receiving threads
		int receiving_port = BASE_PORT + node_id;
		DVReceiver dvr_receiver = new DVReceiver( receiving_port );
		Thread receiver_thread = new Thread( dvr_receiver );
		receiver_thread.start();

		// Log
		log_area.append( "Listening for TCP traffic on port " + receiving_port + "\n" );

		// Process any received Distance Vector Row
		while( true )
		{
			// Pop the first element from received list
			received_dvr = dvr_receiver.get_queue().poll();

			if ( received_dvr != null )
			{
				// Logs received Distance Vector Row
				String dvr_string = new String();
				for ( Map.Entry<Integer, Integer> entry : received_dvr.get_dvr().entrySet() ) 
				{
					dvr_string += entry.getKey() + "(" + entry.getValue() + "), ";
				}
				log_area.append( "Received from " + received_dvr.get_id() + ": " + dvr_string + ".\n" );
					
				// Changes current edge's DVRow to received DVRow if it's different
				if ( !edge_dvr.get( received_dvr.get_id() ).get_dvr().equals( received_dvr.get_dvr() ) )
				{
					edge_dvr.put( received_dvr.get_id(), received_dvr );
					// Update the changes to Distance Table Vector that being used in the JTable
					int i = 1;
					for ( Map.Entry<Integer, Integer> entry : edge_cost.entrySet() ) 
					{
						if ( received_dvr.get_id() == entry.getKey() ) 
						{
							dt_vector.set( i, convert_dvr_to_vector( received_dvr ) );
							break;
						}
						i++;
					}

					// Log the changes and refresh the JTable to show changes
					log_area.append( "Updated received DT to neighbor.\n" );
					refresh_table();

					// Update the main DV row and report if any changes were made
					if ( main_dvr_change() ) 
					{
						// Log the changes and refresh the JTable to show changes
						refresh_table();
						log_area.append( "Updated Primary DT using Bellman-Ford.\n" );

						// Broadcast the changes
						broadcast_dvr();
					} 
					else 
					{
						log_area.append( "Primary DT up-to-date. No need to broadcast changes.\n" );
					}
					send_dvr_node( BASE_PORT );
					log_area.append( "Sent updated DT to master\n" );
				} 				
			}
			TimeUnit.MILLISECONDS.sleep( 500 );
		}
	}
	
	// Send primary distance vector table through socket
	void send_dvr_node( int outgoing_port ) throws UnknownHostException, IOException 
	{
		// Socket and Output stream variables
		Socket out_socket = new Socket( "127.0.0.1", outgoing_port );
		OutputStream out_stream = out_socket.getOutputStream();
		ObjectOutputStream ob_out_stream = new ObjectOutputStream( out_stream );

		//Write and flush socket helps with errors
		ob_out_stream.writeObject( primary_dvr );
		out_stream.flush();
		out_socket.close();
	}

	// Refresh JTable to show user the changes on the GUI
	void refresh_table() 
	{
		DefaultTableModel table_model = ( ( DefaultTableModel ) dt_table.getModel() );
		table_model.fireTableDataChanged();
		dt_table.setModel( table_model );
	}

	// Setup All the GUIs
	void setup_gui() 
	{
		// Variables
		JFrame frame = new JFrame( "Node " + node_id );
		Vector<Vector<String>> edge_vector = get_edge_vector();
		JTable edge_table = new JTable( edge_vector, get_header_vector().get( 1 ) );
		JScrollPane nbr_scroll = new JScrollPane( edge_table );
		JScrollPane dt_scroll = new JScrollPane( dt_table );
		JScrollPane log_scroll = new JScrollPane( log_area );
		Container main_container = new Container();
		JPanel panel = new JPanel();
		DefaultTableCellRenderer c_render = new DefaultTableCellRenderer();
		
		// Alignment for all JTables
		c_render.setHorizontalAlignment( SwingConstants.CENTER );
		// Center Align neighbor data
		edge_table.setDefaultRenderer( String.class, c_render );
		for ( int i = 1; i < edge_table.getColumnCount(); i++ ) 
		{
			edge_table.getColumnModel().getColumn( i ).setCellRenderer( c_render );
		}
		// Center Align Main DT data
		dt_table.setDefaultRenderer( String.class, c_render );
		for ( int i = 1; i < dt_table.getColumnCount(); i++ ) 
		{
			dt_table.getColumnModel().getColumn( i ).setCellRenderer( c_render );
		}

		// Setting custom sizes for each JScrollPane
		nbr_scroll.setMaximumSize( new Dimension( 450, 50 ) );
		dt_scroll.setMaximumSize( new Dimension( 450, 125 ) );
		log_scroll.setMaximumSize( new Dimension( 450, 175 ) );

		// Setup container
		main_container.add( nbr_scroll );
		main_container.add( dt_scroll );
		main_container.add( log_scroll );
		main_container.setLayout( new BoxLayout( main_container, BoxLayout.Y_AXIS ) );

		// Setup main scroller
		panel.add( main_container );

		// Mainframe modification and show
		frame.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );
		frame.add( panel );
		frame.setSize( 475, 400 );
		frame.setVisible( true );

		// Setup table listening events
		edge_table.getModel().addTableModelListener( ( TableModelListener ) new TableModelListener() 
		{
			public void tableChanged( TableModelEvent e )
			{
				// Comparing changes with existing values from edge table
				if ( ! edge_vector.get( 0 ).equals( get_edge_vector().get( 0 ) ) )
				{
					int j = 1;
					for ( Map.Entry<Integer, Integer> entry : edge_cost.entrySet() )
					{
						edge_cost.put( entry.getKey(), Integer.parseInt( edge_vector.get( 0 ).get( j ) ) );
						j++;
					}

					log_area.append( "Neighbor link changed. Updating DV.\n" );
					// Update the main DV row and report if any changes were made
					if ( main_dvr_change() ) 
					{
						refresh_table();
						// Log for changes
						log_area.append( "Updated Primary DT using Bellman-Ford.\n" );
	
						try {
							broadcast_dvr();
							send_dvr_node( BASE_PORT );
							log_area.append( "Sent updated DT to master\n" );
						} catch ( UnknownHostException e1 ) {
							log_area.append( "Node: Unknown Host Exception\n" );
							e1.printStackTrace();
						} catch ( IOException e1 ) {
							log_area.append( "Node: IO Exception\n" );
							e1.printStackTrace();
						} catch ( InterruptedException e1 ) {
							log_area.append( "Node: Interrupt Exception\n" );
							e1.printStackTrace();
						}
					} 				
				}
			}
		} );

	}

	// Overriding Run method
	@Override
	public void run() 
	{
		setup_gui();

		// Setup timer to broadcast primary DVRow
		// with every 3 seconds with initial 2 seconds delay
		Timer timer = new Timer();
		timer.schedule( new TimerTask()
		{
			@Override
			public void run() 
			{
				try {
					broadcast_dvr();
				} catch ( UnknownHostException e ) {
					log_area.append( "Unknown Host Exception\n" );
					e.printStackTrace();
				} catch ( IOException e ) {
					log_area.append( "IO Exception\n" );
					e.printStackTrace();
				} catch ( InterruptedException e ) {
					log_area.append( "Interruptted Exception\n" );
					e.printStackTrace();
				}
			}
		}, 2000, 3000 );

		// Beging sending and receiving nodes
		try {
			send_and_receive();
		} catch ( IOException | InterruptedException e ) {
			log_area.append( "IO/Interrupted Exception\n" );
			e.printStackTrace();
		}
		
	}
	
}