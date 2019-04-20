import java.awt.Container;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.Thread;
import java.lang.reflect.InvocationTargetException;
import java.net.UnknownHostException;
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
import java.util.Scanner;
import java.util.Map;
import java.util.HashMap;
import java.util.Vector;
import java.util.concurrent.TimeUnit;

public class DVMaster 
{
	// Constants
	static final int BASE_PORT = 5680;

	// Variables
	private static int stable_count = 10;
	private static boolean stable_state = false;
	private static File input_file = null;
	private static boolean valid_file = false;
	// Primary HashMap list of imported nodes
	private static HashMap<Integer, DVRow> list;
	// HashMap of all Distance Vector nodes
	private static HashMap<Integer, DVRow> dv_data_map;
	// A 2D vector version of dv_data_map as an input for JTable
	private static Vector<Vector<String>> dv_data_vector;
	private static DVReceiver dvr_receiver;
	
	// GUI variables
	private static JTable dv_table;
	private static JTextArea log_area;

	// Importing data into data structure
	private static void data_import( File file_input ) 
		throws FileNotFoundException 
	{
		// Define list into memory
		list = new HashMap<Integer, DVRow>();
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
				list.put( line_val[0], new DVRow( line_val[0], new HashMap<Integer, Integer>() 
				{
					private static final long serialVersionUID = 1L;
					{
						put( line_val[1], line_val[2] );
					}
				} ) );
			} 
			else 
			{
				HashMap<Integer, Integer> dt = list.get( line_val[0] ).get_dvr();
				if ( !dt.containsKey( line_val[1] ) ) 
				{
					dt.put( line_val[1], line_val[2] );
				}
			}

			// Add link from index 1 to 0 with cost at index 2
			if ( !list.containsKey( line_val[1] ) ) 
			{
				list.put( line_val[1], new DVRow( line_val[1], new HashMap<Integer, Integer>() 
				{
					private static final long serialVersionUID = 1L;

					{
						put( line_val[0], line_val[2] );
					}
				} ) );
			} 
			else 
			{
				HashMap<Integer, Integer> dt = list.get( line_val[1] ).get_dvr();
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
		long start_time = System.currentTimeMillis();

		// Infinite Loop
		while( true )
		{
			// Check if the received data queue is empty before processing
			if( dvr_receiver.get_queue().size() != 0 )
			{
				update_main_dt();
				stable_count = 6;
			}
			else
			{
				if ( stable_count < 0 )
				{
					stable_count = 0;
				}
				else if ( stable_count == 0 )
				{
					if( stable_state == false )
					{
						log_area.append( "Stable state reached!\n" );
						long dif = ( System.currentTimeMillis() - start_time ) / 1000;
						log_area.append( "Total Time: " + dif + " seconds\n" );
						stable_state = true;
					}
				}
				else
				{
					stable_count--;
					if( stable_state == true )
					{
						stable_state = false;
					}
				}
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
		dv_data_map = new HashMap<Integer, DVRow>();
		dv_data_vector = new Vector<Vector<String>>();

		// Setting up header vector information

		Vector<String> dv_header = new Vector<String>();
		dv_header.add( "D( v, y )" );

		// Iterate through all nodes to populate values into dv_data_map
		for( int i = 1; i <= list.size(); i++ )
		{
			// New row with infinity values
			HashMap<Integer, Integer> new_row = DVNode.create_dvr_map( list.size() );
			// Iterate through imported list of nodes
			for ( Map.Entry<Integer, DVRow> j : list.entrySet() )
			{
				if ( j.getKey() == i )
				{
					// Iterate through neighbors of each node
					for ( Map.Entry<Integer, Integer> k : j.getValue().get_dvr().entrySet() )
					{
						new_row.put( k.getKey() , k.getValue() );
					}
				}
			}
			// Set zero value for same source and destination
			DVRow temp_dv_row = DVNode.zero_self( new DVRow( i, new_row ), i );
			
			// Set updated new row to data map and vector
			dv_data_map.put( i , temp_dv_row );
			dv_data_vector.add( DVNode.convert_dvr_to_vector( temp_dv_row ) );
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
		log_area = new JTextArea();

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

		// Center Align Main DT data
		dv_table.setDefaultRenderer( String.class, c_render );
		for ( int i = 1; i < dv_table.getColumnCount(); i++ ) 
		{
			dv_table.getColumnModel().getColumn( i ).setCellRenderer( c_render );
		}

		// Set size for each component
		step_scroll.setMaximumSize( new Dimension( 450, 35 ) );
		dv_scroll.setMaximumSize( new Dimension( 450, 125 ) );
		log_scroll.setMaximumSize( new Dimension( 450, 175 ) );
		
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
				if ( dvr_receiver.get_queue().peek() != null )
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
		frame.setSize( 475, 400 );
		frame.setVisible( true );
	}

	// Initialize program
	private static void setup_threads() 
				throws UnknownHostException, IOException, InterruptedException
	{
		// Start Master receiving threads
		dvr_receiver = new DVReceiver( BASE_PORT );		
		Thread receiver_thread = new Thread( dvr_receiver );
		receiver_thread.start();
		log_area.append( "Master node listening on port " + BASE_PORT + "\n" );

		// Give Master Receiver node time to get ready to accept connections
		TimeUnit.SECONDS.sleep( 1 );

		// Create a Map of all master Node( s )
		HashMap<Integer, DVNode> node_list = new HashMap<Integer, DVNode>();
		for( int i = 1; i <= list.size(); i++ )
		{
			node_list.put( i, new DVNode( i, list, false ) );
		}
		// Run each node with one thread per node
		for ( Map.Entry<Integer, DVNode> entry : node_list.entrySet() )
		{
			Thread temp_thread = new Thread( entry.getValue() );
			temp_thread.start();
		}
	}

	// Update Distance Vector data
	private static void update_main_dt()
	{
		// Pop an DVRow received and process changes to the table
		DVRow received_dt = dvr_receiver.get_queue().poll();

		// Compare received data and current data to see which is more up-to-date
		int received_id = received_dt.get_id();
		if ( !received_dt.get_dvr().equals( dv_data_map.get( received_id ).get_dvr() ) )
		{
			// Updating local data with received data
			dv_data_map.put( received_id, received_dt );
			dv_data_vector.set( received_id - 1, DVNode.convert_dvr_to_vector
												( dv_data_map.get( received_id ) ) );
			
			DefaultTableModel table_model = ( ( DefaultTableModel ) dv_table.getModel() );
			table_model.fireTableDataChanged();
			dv_table.setModel( table_model );	
			// Update logs
			log_area.append( "Received Node " + received_id + 
				" is more up-to-date. Updated Node " + received_id + " locally\n" );
		}
		else
		{
			// Update logs
			log_area.append( "Current Node " + received_id + 
				" is more up-to-date. No changes to Node " + received_id + " locally\n" );
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