import java.util.Scanner;
import java.util.Map;
import java.util.Random;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.util.HashMap;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.TimeUnit;
import java.util.HashSet;
import java.lang.Thread;
import java.lang.reflect.InvocationTargetException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.EventQueue;

import javax.swing.BoxLayout;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

public class DVRoute 
{
	// Variables
	// Constants
	static final int BASE_PORT = 5680;

	// Helper variables
	private static File input_file = null;
	private static boolean got_file = false;
	private static boolean dv_change;

	// Data variables
	private static HashMap<Integer, DTNode> list;
	private static HashMap<Integer, DTNode> dv_data_map;
	private static Vector<Vector<String>> dv_data_vector;
	private static Set<Integer> node_set;
	
	// GUI variables
	private static JFrame frame;
	private static JTable dv_table;
	private static JTextArea log_area;
	
	public static HashMap<Integer, DTNode> data_import( File file_input ) 
	throws FileNotFoundException 
	{
		// Variables

		HashMap<Integer, DTNode> input_list = new HashMap<Integer, DTNode>();
		Scanner input = new Scanner( file_input );

		while ( input.hasNextLine() ) 
		{
			// Read and split each line into accessible array
			String line = input.nextLine();
			String[] line_split = line.split( "\\s+" );
			int[] line_val = new int[line_split.length];

			// Parse each element into integer
			for ( int i = 0; i < line_split.length; i++ ) 
			{
				line_val[i] = Integer.parseInt( line_split[i] );
			}

			// Add link from index 0 to 1 with cost at index 2
			if ( !input_list.containsKey( line_val[0] ) ) 
			{
				input_list.put( line_val[0], new DTNode( line_val[0], new HashMap<Integer, Integer>() 
				{
					private static final long serialVersionUID = 1L;

					{
						put( line_val[1], line_val[2] );
					}
				} ) );
			} 
			else 
			{
				HashMap<Integer, Integer> dt = input_list.get( line_val[0] ).get_dt();
				if ( !dt.containsKey( line_val[1] ) ) 
				{
					dt.put( line_val[1], line_val[2] );
				}
			}

			// Add link from index 1 to 0 with cost at index 2
			if ( !input_list.containsKey( line_val[1] ) ) 
			{
				input_list.put( line_val[1], new DTNode( line_val[1], new HashMap<Integer, Integer>() 
				{
					private static final long serialVersionUID = 1L;

					{
						put( line_val[0], line_val[2] );
					}
				} ) );
			} 
			else 
			{
				HashMap<Integer, Integer> dt = input_list.get( line_val[1] ).get_dt();
				if ( !dt.containsKey( line_val[0] ) ) 
				{
					dt.put( line_val[0], line_val[2] );
				}
			}
		}

		input.close();
		return input_list;
	}

	// Setup all data structure
	private static void setup_data() throws FileNotFoundException
	{
		// Data variables
		// Importing data text into file
		list = data_import( input_file );

		// Define Map and vector for master table DV nodes
		dv_data_map = new HashMap<Integer, DTNode>();
		dv_data_vector = new Vector<Vector<String>>();

		// Setting up header vector information
		node_set = new HashSet<Integer>();
		Vector<String> dv_header = new Vector<String>();
		dv_header.add( "D( v, y )" );
		for ( int i = 1; i <= list.size(); i++ )
		{
			node_set.add( i );
		}

		// Iterate through all nodes to populate values into dv_data_map
		for( Integer i : node_set )
		{
			// New row with infinity values
			HashMap<Integer, Integer> new_row = Node.to_infinity_and_beyond( node_set );
			// Iterate through imported list of nodes
			for ( Map.Entry<Integer, DTNode> j : list.entrySet() )
			{
				if ( j.getKey() == i )
				{
					// Iterate through neighbors of each node
					for ( Map.Entry<Integer, Integer> k : j.getValue().get_dt().entrySet() )
					{
						new_row.put( k.getKey() , k.getValue() );
					}
				}
			}
			// Set zero value for same source and destination
			DTNode temp_dt_node = Node.zero_self( new DTNode( i, new_row ), i );
			
			// Set updated new row to data map and vector
			dv_data_map.put( i , temp_dt_node );
			dv_data_vector.add( Node.dt_to_vector( temp_dt_node ) );
		}		
	}
	
	// Setup GUI
	private static void setup_gui()
	{
		Vector<String> dv_header = new Vector<String>();
		dv_header.add( "D( v, y )" );
		for ( int i = 1; i <= list.size(); i++ )
		{
			dv_header.add( "Node " + Integer.toString( i ) );
		}

		// GUI Variables
		frame = new JFrame( "Master" );
		dv_table = new JTable( dv_data_vector, dv_header );
		log_area = new JTextArea( "Log for master node\n" );
		JScrollPane dv_scroll = new JScrollPane( dv_table );
		JScrollPane log_scroll = new JScrollPane( log_area );

		// Alignment for all JTables
		DefaultTableCellRenderer c_render = new DefaultTableCellRenderer();
		c_render.setHorizontalAlignment( SwingConstants.CENTER );

		// Center Align neighbor data
		dv_table.setDefaultRenderer( String.class, c_render );
		for ( int i = 1; i < dv_table.getColumnCount(); i++ ) 
		{
			dv_table.getColumnModel().getColumn( i ).setCellRenderer( c_render );
		}

		// Center Align Main DT data
		dv_table.setDefaultRenderer( String.class, c_render );
		for ( int i = 1; i < dv_table.getColumnCount(); i++ ) 
		{
			dv_table.getColumnModel().getColumn( i ).setCellRenderer( c_render );
		}

		// Set size for each component
		dv_scroll.setMaximumSize( new Dimension( 450, 110 ) );
		log_scroll.setMaximumSize( new Dimension( 450, 200 ) );

		// Store components into Container
		Container container = new Container();
		container.add( dv_scroll );
		container.add( log_scroll );
		container.setLayout( new BoxLayout( container, BoxLayout.Y_AXIS ) );

		// Store container into Panel
		JPanel panel = new JPanel();
		panel.add( container );

		// Set frame properties and display
		frame.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );
		frame.add( panel );
		frame.setSize( 475, 360 );
	}

	// Initialize program
	private static void start_program() 
		throws UnknownHostException, IOException, InterruptedException
	{
		// Create a Map of all master Node( s )
		HashMap<Integer, Node> node_list = new HashMap<Integer, Node>();
		for ( int i : node_set )
		{
			node_list.put( i, new Node( i, list, node_set, false ) );
		}
		// Run each node with one thread per node
		for ( Map.Entry<Integer, Node> entry : node_list.entrySet() )
		{
			Thread temp_thread = new Thread( entry.getValue() );
			temp_thread.start();
		}
		frame.setVisible( true );


		// Have one random node except node 1 to broadcast changes
		Random rand_gen = new Random();
		int start_node = rand_gen.nextInt( node_list.size() - 1 ) + 2;
		Node start = node_list.get( start_node );
		log_area.append( "Node " + start_node + " started broadcasting\n" );
		start.broadcast_dt();
	}


	// Setup network for receiving data
	private static void start_network() throws IOException, ClassNotFoundException, InterruptedException
	{
		Socket socket;
		ServerSocket master_socket = new ServerSocket( BASE_PORT );
		log_area.append( "Started listening on port " + BASE_PORT + "\n" );

		while ( true )
		{
			// Set initial change variables
			dv_change = false;

			// Socket variables
			socket = master_socket.accept();

			// Using input stream to receive objects
			InputStream in_stream = socket.getInputStream();
			ObjectInputStream ob_in_stream = new ObjectInputStream( in_stream );

			// Reading incoming object and cast it to DTNode
			DTNode received_dt = ( DTNode ) ob_in_stream.readObject();
		
			// Logs
			log_area.append( "Received from Node " + received_dt.get_id() + ": " );
			log_area.append( Node.dt_to_string( received_dt ) + "\n" );

			if ( !received_dt.get_dt().equals( dv_data_map.get( received_dt.get_id() ).get_dt() ) )
			{
				// Updating local data with received data
				dv_data_map.put( received_dt.get_id(), received_dt );
				dv_data_vector.set( received_dt.get_id() - 1, 
					Node.dt_to_vector( dv_data_map.get( received_dt.get_id() ) ) );
				
				DefaultTableModel table_model = ( ( DefaultTableModel ) dv_table.getModel() );
				table_model.fireTableDataChanged();
				dv_table.setModel( table_model );	
				
				// Update logs
				log_area.append( "Received data is more up-to-date. Updated local\n" );

				// Update change variables
				dv_change = true;
			}
			else
			{
				// Update logs
				log_area.append( "Current data is more up-to-date. Disregard changes\n" );
			}

			// Exit the program based on changes detection
			if ( dv_change == false )
			{
				log_area.append( "Stable state detected. Exiting program " );
				master_socket.close();
				break;
			}
			TimeUnit.MILLISECONDS.sleep(500);
		}
	}

	public static void main( String[] args )
			throws FileNotFoundException, IOException, UnknownHostException, 
			InterruptedException, InvocationTargetException, ClassNotFoundException
  	{
		// Selecting File starting with current directory through JFileChooser
		EventQueue.invokeAndWait( new Runnable() 
		{
			@Override
			public void run() 
			{
				JFileChooser jfc = new JFileChooser( new File( System.getProperty( "user.dir" ) ) );
				if( jfc.showOpenDialog( null ) == JFileChooser.APPROVE_OPTION )
				{
					input_file = jfc.getSelectedFile();
					got_file = true;
				}
			}
		} );

		if( got_file )
		{
			
			setup_data();
			setup_gui();

			start_program();
			start_network();


					
		}
		else
		{
			System.exit( 0 );
		}
		
	}
	
}