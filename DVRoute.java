import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Scanner;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.TimeUnit;
import java.util.HashSet;
import java.lang.Thread;
import java.lang.reflect.InvocationTargetException;
import java.net.UnknownHostException;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.BoxLayout;
import javax.swing.JButton;
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
	// Constants
	static final int BASE_PORT = 5680;

	// Variables
	private static File input_file = null;
	private static boolean valid_file = false;
	// Primary HashMap list of imported nodes
	private static HashMap<Integer, DTNode> list;
	// HashMap of all Distance Vector nodes
	private static HashMap<Integer, DTNode> dv_data_map;
	// A 2D vector version of dv_data_map as an input for JTable
	private static Vector<Vector<String>> dv_data_vector;
	private static DTReceiver dt_receiver;
	
	
	// GUI variables
	private static JTable dv_table;
	private static JTextArea log_area;
	
	// Create temporary Set for all nodes
	private static Set<Integer> create_temp_set()
	{
		Set<Integer> temp_set = new HashSet<Integer>();
		for ( int j = 1; j <= list.size(); j++ )
		{
			temp_set.add( j );
		}
		return temp_set;		
	}

	// Importing data into data structure
	private static void data_import( File file_input ) 
		throws FileNotFoundException 
	{
		// Define list into memory
		list = new HashMap<Integer, DTNode>();
		Scanner input = new Scanner( file_input );

		// Scanning text file to import data
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
			if ( !list.containsKey( line_val[0] ) ) 
			{
				list.put( line_val[0], new DTNode( line_val[0], new HashMap<Integer, Integer>() 
				{
					private static final long serialVersionUID = 1L;
					{
						put( line_val[1], line_val[2] );
					}
				} ) );
			} 
			else 
			{
				HashMap<Integer, Integer> dt = list.get( line_val[0] ).get_dt();
				if ( !dt.containsKey( line_val[1] ) ) 
				{
					dt.put( line_val[1], line_val[2] );
				}
			}

			// Add link from index 1 to 0 with cost at index 2
			if ( !list.containsKey( line_val[1] ) ) 
			{
				list.put( line_val[1], new DTNode( line_val[1], new HashMap<Integer, Integer>() 
				{
					private static final long serialVersionUID = 1L;

					{
						put( line_val[0], line_val[2] );
					}
				} ) );
			} 
			else 
			{
				HashMap<Integer, Integer> dt = list.get( line_val[1] ).get_dt();
				if ( !dt.containsKey( line_val[0] ) ) 
				{
					dt.put( line_val[0], line_val[2] );
				}
			}
		}
		input.close();
	}

	// Run the program in one single step
	private static void single_step_mode() throws InterruptedException
	{
		// Infinite Loop
		while( true )
		{
			// Check if the received data queue is empty before processing
			if( dt_receiver.get_queue().size() != 0 )
			{
				update_main_dt();
			}
			// Sleep the program to not spike the CPU
			TimeUnit.MILLISECONDS.sleep( 500 );
		}
	}

	// Setup all data structure
	private static void setup_data() throws FileNotFoundException
	{
		// Importing data text into file
		data_import( input_file );

		// Define Map and vector for master table DV nodes
		dv_data_map = new HashMap<Integer, DTNode>();
		dv_data_vector = new Vector<Vector<String>>();

		// Setting up header vector information

		Vector<String> dv_header = new Vector<String>();
		dv_header.add( "D( v, y )" );

		// Iterate through all nodes to populate values into dv_data_map
		for( int i = 1; i <= list.size(); i++ )
		{
			// New row with infinity values
			HashMap<Integer, Integer> new_row = Node.to_infinity_and_beyond( create_temp_set() );
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
	private static void setup_gui( Boolean step_flag )
	{
		// Setup table headers
		Vector<String> dv_header = new Vector<String>();
		dv_header.add( "D( v, y )" );
		for ( int i = 1; i <= list.size(); i++ )
		{
			dv_header.add( "Node " + Integer.toString( i ) );
		}
		dv_table = new JTable( dv_data_vector, dv_header );

		// GUI Variables
		JFrame frame = new JFrame( "Master" );
		JButton step_button = new JButton( "Step" );
		JScrollPane dv_scroll = new JScrollPane( dv_table );
		JScrollPane log_scroll = new JScrollPane( log_area );
		JScrollPane step_scroll = new JScrollPane( step_button );
		Container container = new Container();
		JPanel panel = new JPanel();
		DefaultTableCellRenderer c_render = new DefaultTableCellRenderer();
		

		// Setting Center Alignment variable
		c_render.setHorizontalAlignment( SwingConstants.CENTER );

		// // Center Align neighbor data
		// dv_table.setDefaultRenderer( String.class, c_render );
		// for ( int i = 1; i < dv_table.getColumnCount(); i++ ) 
		// {
		// 	dv_table.getColumnModel().getColumn( i ).setCellRenderer( c_render );
		// }

		// Center Align Main DT data
		dv_table.setDefaultRenderer( String.class, c_render );
		for ( int i = 1; i < dv_table.getColumnCount(); i++ ) 
		{
			dv_table.getColumnModel().getColumn( i ).setCellRenderer( c_render );
		}

		// Set size for each component
		dv_scroll.setMaximumSize( new Dimension( 450, 110 ) );
		log_scroll.setMaximumSize( new Dimension( 450, 200 ) );
		step_scroll.setMaximumSize( new Dimension( 450, 35 ) );

		// Store components into Container and JPanel,  and align vertically
		container.add( step_scroll );
		container.add( dv_scroll );
		container.add( log_scroll );
		container.setLayout( new BoxLayout( container, BoxLayout.Y_AXIS ) );
		panel.add( container );

		//	Enable Step-Button if step flag is true
		if( step_flag == true )
		{
			step_button.setEnabled( false );
		}
		// Step button listener event
		step_button.addActionListener( new ActionListener()
		{
			public void actionPerformed( ActionEvent e )
			{
				if ( dt_receiver.get_queue().peek() != null )
				{
					update_main_dt();
				}
				else
				{
					log_area.append( "Reached Stable state. No more clicking\n" );
				}
			}
		});

		// Set frame properties and display GUI elements
		frame.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );
		frame.add( panel );
		frame.setSize( 475, 475 );
		frame.setVisible( true );
	}

	// Initialize program
	private static void setup_threads() 
				throws UnknownHostException, IOException, InterruptedException
	{
		// Start Master receiving threads
		dt_receiver = new DTReceiver( BASE_PORT );		
		Thread receiver_thread = new Thread( dt_receiver );
		receiver_thread.start();
		log_area.append( "Master node listening on port " + BASE_PORT + "\n" );

		// Give Master Receiver node time to get ready to accept connections
		TimeUnit.SECONDS.sleep( 1 );

		// Create a Map of all master Node( s )
		HashMap<Integer, Node> node_list = new HashMap<Integer, Node>();
		for( int i = 1; i <= list.size(); i++ )
		{
			node_list.put( i, new Node( i, list, create_temp_set(), false ) );
		}
		// Run each node with one thread per node
		for ( Map.Entry<Integer, Node> entry : node_list.entrySet() )
		{
			Thread temp_thread = new Thread( entry.getValue() );
			temp_thread.start();
		}
	}

	// Update Distance Vector data
	private static void update_main_dt()
	{
		// Pop an DTNode received and process changes to the table
		DTNode received_dt = dt_receiver.get_queue().poll();

		// Compare received data and current data to see which is more up-to-date
		int received_id = received_dt.get_id();
		if ( !received_dt.get_dt().equals( dv_data_map.get( received_id ).get_dt() ) )
		{
			// Updating local data with received data
			dv_data_map.put( received_id, received_dt );
			dv_data_vector.set( received_id - 1, Node.dt_to_vector
												( dv_data_map.get( received_id ) ) );
			
			DefaultTableModel table_model = ( ( DefaultTableModel ) dv_table.getModel() );
			table_model.fireTableDataChanged();
			dv_table.setModel( table_model );	
			// Update logs
			log_area.append( "Received data is more up-to-date. Updated local\n" );
		}
		else
		{
			// Update logs
			log_area.append( "Current data is more up-to-date. Disregard changes\n" );
		}
	}

	// Main method
	public static void main( String[] args ) throws 
				FileNotFoundException, IOException, UnknownHostException, 
				InterruptedException, InvocationTargetException, ClassNotFoundException
  	{
		// Variables
		Boolean step_flag = false;

		// Check for other input parameters for Step by Step mode
		if( args.length == 1 )
		{
			if( args[0].equals( "-s" ) );
			{
				step_flag = true;
			}
		}

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
					valid_file = true;
				}
			}
		} );

		// Proceed if the file is valid
		if( valid_file )
		{			
			// Method calls setting up Data, GUI, and spawn threads
			setup_data();
			setup_gui( step_flag );
			setup_threads();

			// Run the program in single click mode if needs to
			if( step_flag == true )
			{
				single_step_mode();
			}
		}
		else
		{
			System.exit( 0 );
		}
	}
}