import java.awt.Container;
import java.awt.Dimension;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.net.ServerSocket;
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
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class Node implements Runnable {
	private final int BASE_PORT = 5680;
	private Socket socket;
	private ServerSocket server_socket;
	private int receiving_port;

	// Class Distance Vector variables
	private int node_id; // Primary Node ID
	private DTNode node_dt; // Primary DTNode
	private DTNode received_dt; // Received DTNode from others
	private Map<Integer, Integer> nbr_val; // Neighbor c(S,D) values
	private Map<Integer, DTNode> nbr_dt; // Neighbor DT list
	private Set<Integer> node_set; // Store list of all nodes
	private Vector<Vector<String>> dt_vector;
	private DTReceiver node_dt_rec;
	Vector<Vector<String>> nbr_data_table;

	// GUI Variables
	private JTextArea log_area;
	private JTable dt_table;
	JTable nbr_table;

	// Constructor
	Node(int id, HashMap<Integer, DTNode> list, Set<Integer> nbr_set, Boolean step ) 
	{
		// Variable definition
		nbr_val = new HashMap<Integer, Integer>();
		nbr_dt = new HashMap<Integer, DTNode>();
		dt_vector = new Vector<Vector<String>>();

		// Variable assignments
		node_id = id;
		node_set = nbr_set;
		receiving_port = BASE_PORT + node_id;

		// Add Neighbor information
		for (Map.Entry<Integer, Integer> entry : list.get(node_id).get_dt().entrySet()) 
		{
			// Add neighbor and corresponding C(x, v) value
			nbr_val.put(entry.getKey(), entry.getValue());
			// Add Neighbor infinity DV
			nbr_dt.put(entry.getKey(), new DTNode(entry.getKey(), to_infinity_and_beyond(node_set)));
		}

		// Prep primary DT
		node_dt = new DTNode(node_id, to_infinity_and_beyond(node_set));
		// Changed DV value of neighboring node to non-infinity values
		for (Map.Entry<Integer, DTNode> entry : list.entrySet()) 
		{
			if (nbr_val.containsKey(entry.getKey())) 
			{
				node_dt.get_dt().put(entry.getKey(), nbr_val.get(entry.getKey()));
			}
		}
		zero_self(node_dt, node_id);

		dt_vector = get_dt_vector();
		dt_table = new JTable(dt_vector, get_header_vector().get(0));
	}

	// Getters
	int get_id() { return node_id; }
	DTNode get_main_dt() { return node_dt; }
	Map<Integer, DTNode> get_nbr_dt() { return nbr_dt; }

	// Setters
	void set_main_dt(DTNode dt) { node_dt = dt; }
	void set_nbr_dt(HashMap<Integer, DTNode> dt_nbr) { nbr_dt = dt_nbr; }
	void set_rec_dt(DTNode update_dt) { received_dt = update_dt; }
	void set_nbr_list(HashMap<Integer, Integer> nbr_list) { nbr_val = nbr_list; }

	// Check if incoming DTNode exist and different from current
	boolean check_change_flag() 
	{
		int received_id = received_dt.get_id();
		// Check if received DTNode ID exists or if it's from a neighboring node or not
		if (nbr_dt.containsKey(received_id)) 
		{
			// Check if received DTNode DV Table is the same as Received DTNode DV Table
			if (!nbr_dt.get(received_id).get_dt().equals(received_dt.get_dt())) 
			{
				return true;
			}
		}
		return false;
	}

	// Update primary DVTable using Bellman-Ford
	boolean main_dv_change() 
	{
		// Store current primary DV to compare changes later
		boolean change_flag = false;

		// Iterate through all keys in primary dv
		Map<Integer, Integer> dv_table = node_dt.get_dt();
		for (Map.Entry<Integer, Integer> dv_entry : dv_table.entrySet()) // Each node in primary D table
		{
			// Destination ID Dest
			int dest_val = dv_entry.getKey();
			// Candidates to select minimum values from
			ArrayList<Integer> dv_candidates = new ArrayList<Integer>();
			// Update if destination node doesn't match primary node
			if (dv_entry.getKey() != node_id) 
			{
				// Iterate through each node in the neighbor to calculate c( Src, Nbr )
				for (Map.Entry<Integer, Integer> nbr_entry : nbr_val.entrySet()) 
				{
					int nbr_num = nbr_entry.getValue();
					int nbr_to_dest = nbr_dt.get(nbr_entry.getKey()).get_dt().get(dest_val);
					dv_candidates.add(nbr_num + nbr_to_dest);
				}

				// Set the min value from the calculation as new DV if it's less than current
				int min_dv = Collections.min(dv_candidates);
				HashMap<Integer, Integer> temp_dv = node_dt.get_dt();

				// Compare min value with current
				if (min_dv < node_dt.get_dt().get(dest_val)) 
				{
					temp_dv.put(dest_val, min_dv);
					change_flag = true;
					node_dt.set_dt(temp_dv);
					dt_vector.set(0, dt_to_vector(node_dt));

				}
			}
		}

		// Return boolean change result
		if (change_flag) 
		{
			return true;
		}
		return false;
	}

	// Replace any entries from node to itself with zero
	public static DTNode zero_self(DTNode dt, int self_val) 
	{
		HashMap<Integer, Integer> nbrdt = dt.get_dt();
		nbrdt.put(self_val, 0);
		dt.set_dt(nbrdt);
		return dt;
	}

	// Produce a distance vector row with all values @ 99
	public static HashMap<Integer, Integer> to_infinity_and_beyond(Set<Integer> node_list) 
	{
		HashMap<Integer, Integer> dt = new HashMap<Integer, Integer>();
		for (Integer i : node_list) 
		{
			dt.put(i, 16);
		}
		return dt;
	}

	// Return a distance vector row in string format given a DTNode object
	public static String dt_to_string(DTNode dt_row) 
	{
		String result = new String();
		for (Map.Entry<Integer, Integer> entry : dt_row.get_dt().entrySet()) 
		{
			result += entry.getKey() + "(" + entry.getValue() + "), ";
		}
		return result;
	}

	// Send primary distance vector table through socket
	void send_dt_node(int outgoing_port ) 
		throws UnknownHostException, IOException 
		{
		Socket out_socket = new Socket("127.0.0.1", outgoing_port);
		// Creating object output stream
		OutputStream out_stream = out_socket.getOutputStream();
		ObjectOutputStream ob_out_stream = new ObjectOutputStream( out_socket.getOutputStream() );

		//Write and flush socket helps with errors
		ob_out_stream.writeObject( node_dt );
		out_stream.flush();
		out_socket.close();
	}

	void send_and_receive() throws UnknownHostException, IOException, InterruptedException
	{
		node_dt_rec = new DTReceiver( receiving_port );
		Thread receiver_thread = new Thread( node_dt_rec );
		receiver_thread.start();

		log_area.append("Listening for TCP traffic on port " + receiving_port + "\n");

		while( true )
		{
			received_dt = node_dt_rec.get_queue().poll();

			if ( received_dt != null )
			{
				//received_dt = node_dt_rec.get_queue().remove( 0 );
				

				log_area.append("Received from " + received_dt.get_id() + ": " 
					+ dt_to_string( received_dt ) + ".\n");
					
				// Check if received DV row is different than current
				if (check_change_flag()) 
				{
					// Update current neighbor DV row to received if different
					update_nbr_dt();

					// Set variable
					log_area.append("Updated received DT to neighbor.\n");
					refresh_table();

					// Update the main DV row and report if any changes were made
					if (main_dv_change()) 
					{
						refresh_table();
						// Log for changes
						log_area.append("Updated Primary DT using Bellman-Ford.\n");
						broadcast_dt();
					} 
					else 
					{
						log_area.append("Primary DT up-to-date. No need to broadcast changes.\n");
					}
					send_dt_node( BASE_PORT );
					log_area.append("Sent updated DT to master\n");
				} 				
			}
			TimeUnit.MILLISECONDS.sleep( 500 );
		}
	}

	// Receiving DV information and trigger broadcast changes
	void receive_and_send() throws IOException, ClassNotFoundException, InterruptedException 
	{
		server_socket = new ServerSocket(receiving_port);
		log_area.append("Listening for TCP traffic on port " + receiving_port + "\n");

		// Main loop accepting connection and sending distance vector nodes
		while (true) {
			// Socket accepting incoming connection
			socket = server_socket.accept();

			// Using input stream to receive objects
			ObjectInputStream ob_in_stream = new ObjectInputStream( socket.getInputStream() );

			// Reading incoming object and cast it to DTNode
			received_dt = (DTNode) ob_in_stream.readObject();

			log_area.append("Received from " + received_dt.get_id() + ": " + dt_to_string(received_dt) + ".\n");

			// Check if received DV row is different than current
			if (check_change_flag()) 
			{
				// Update current neighbor DV row to received if different
				update_nbr_dt();

				// Set variable
				log_area.append("Updated received DT to neighbor.\n");
				refresh_table();

				// Update the main DV row and report if any changes were made
				if (main_dv_change()) 
				{
					refresh_table();
					// Log for changes
					log_area.append("Updated Primary DT using Bellman-Ford.\n");
					broadcast_dt();

					send_dt_node( BASE_PORT );
					log_area.append("Sent updated DT to master\n");
				} 
				else 
				{
					log_area.append("Primary DT up-to-date. No need to broadcast changes.\n");
				}
			} 
			else 
			{
				log_area.append("Neighbors' DTs up-to-date. Disregarding received DT.");
			}

			//TimeUnit.SECONDS.sleep(1);
		}
	}

	// Refresh table after data change
	void refresh_table() 
	{
		DefaultTableModel table_model = ((DefaultTableModel) dt_table.getModel());
		table_model.fireTableDataChanged();
		dt_table.setModel(table_model);
	}

	// Setup All the GUIs
	void setup_gui() 
	{
		// Setup log variables log_area
		log_area = new JTextArea("Logs for node " + node_id + "\n");

		// JTable variables
		nbr_data_table = get_nbr_vector();
		nbr_table = new JTable( nbr_data_table, get_header_vector().get(1) );

		// Alignment for all JTables
		DefaultTableCellRenderer c_render = new DefaultTableCellRenderer();
		c_render.setHorizontalAlignment(SwingConstants.CENTER);

		// Center Align neighbor data
		nbr_table.setDefaultRenderer(String.class, c_render);
		for (int i = 1; i < nbr_table.getColumnCount(); i++) 
		{
			nbr_table.getColumnModel().getColumn(i).setCellRenderer(c_render);
		}

		// Center Align Main DT data
		dt_table.setDefaultRenderer(String.class, c_render);
		for (int i = 1; i < dt_table.getColumnCount(); i++) 
		{
			dt_table.getColumnModel().getColumn(i).setCellRenderer(c_render);
		}


		// GUI variables definition and initialization
		JFrame frame = new JFrame("Node " + node_id);
		JScrollPane nbr_scroll = new JScrollPane(nbr_table);
		JScrollPane dt_scroll = new JScrollPane(dt_table);
		JScrollPane log_scroll = new JScrollPane(log_area);

		// Setting custom sizes for each JScrollPane
		nbr_scroll.setMaximumSize(new Dimension(450, 50));
		dt_scroll.setMaximumSize(new Dimension(450, 110));
		log_scroll.setMaximumSize(new Dimension(450, 150));

		// Setup container
		Container main_container = new Container();
		main_container.add( nbr_scroll);
		main_container.add( dt_scroll);
		main_container.add( log_scroll);
		main_container.setLayout(new BoxLayout(main_container, BoxLayout.Y_AXIS));

		// Setup main scroller
		JPanel panel = new JPanel();
		panel.add(main_container);

		// Mainframe modification and show
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.add(panel);
		frame.setSize(500, 400);
		frame.setVisible(true);
	}

	// Broadcast main distance vector node to all neighbor host
	void broadcast_dt() 
		throws UnknownHostException, IOException, InterruptedException 
	{
		for (Map.Entry<Integer, Integer> entry : nbr_val.entrySet()) 
		{
			send_dt_node(BASE_PORT + entry.getKey() );
			log_area.append("Sent updated DT to node " + entry.getKey() + ".\n");
			TimeUnit.MILLISECONDS.sleep(500);
		}
		//send_dt_node( BASE_PORT );
		//log_area.append("Sent updated DT to master\n");
	}

	// Update neighbor Distance Vector Table
	void update_nbr_dt() 
	{
		if (check_change_flag()) 
		{
			nbr_dt.put(received_dt.get_id(), received_dt);

			int i = 1;
			for (Map.Entry<Integer, Integer> entry : nbr_val.entrySet()) 
			{
				if (received_dt.get_id() == entry.getKey()) 
				{
					dt_vector.set(i, dt_to_vector(received_dt));
					break;
				}
				i++;
			}
		}
	}

	// Return a vector that contains a row of distance vector
	public static Vector<String> dt_to_vector(DTNode dtnode) 
	{
		Vector<String> dt = new Vector<String>();
		dt.add("Node " + Integer.toString(dtnode.get_id()));

		for (Map.Entry<Integer, Integer> entry : dtnode.get_dt().entrySet()) 
		{
			dt.add(Integer.toString(entry.getValue()));
		}

		return dt;
	}

	// Return a 2D vector that contains all the node and neighbor DT
	Vector<Vector<String>> get_dt_vector() 
	{
		Vector<Vector<String>> dt = new Vector<Vector<String>>();
		dt.add(dt_to_vector(node_dt));
		for (Map.Entry<Integer, DTNode> entry : nbr_dt.entrySet()) 
		{
			dt.add(dt_to_vector(entry.getValue()));
		}
		return dt;
	}

	// Return a 2D vector that contains header for D(v, y) and c(x, v)
	Vector<Vector<String>> get_header_vector() 
	{
		// DV Table Header Vector
		Vector<String> dt_header = new Vector<String>();
		dt_header.add("D(v, y)");
		for (Integer i : node_set) 
		{
			dt_header.add("Node " + Integer.toString(i));
		}
		// Neighbor Table Header Vector
		Vector<String> nbr_header = new Vector<String>();
		nbr_header.add("C(x, v)");
		for (Map.Entry<Integer, Integer> entry : nbr_val.entrySet()) 
		{
			nbr_header.add("Node " + Integer.toString(entry.getKey()));
		}

		Vector<Vector<String>> header_vector = new Vector<Vector<String>>();
		header_vector.add(dt_header);
		header_vector.add(nbr_header);

		return header_vector;
	}

	// Return a 2D vector that contains neighbor values data
	Vector<Vector<String>> get_nbr_vector() 
	{
		Vector<Vector<String>> nbr_val_vector = new Vector<Vector<String>>();
		Vector<String> data = new Vector<String>();
		data.add("C(" + Integer.toString(node_id) + ",v)");

		// Adding neighbors to DT Vector
		for (Map.Entry<Integer, Integer> entry : nbr_val.entrySet()) 
		{
			data.add(Integer.toString(entry.getValue()));
		}
		nbr_val_vector.add(data);
		return nbr_val_vector;
	}

	void listen_data_change()
	{
		nbr_table.getModel().addTableModelListener( (TableModelListener) new TableModelListener() 
		{
			public void tableChanged( TableModelEvent e )
			{
				if ( ! nbr_data_table.get( 0 ).equals( get_nbr_vector().get( 0 ) ) )
				{
					int j = 1;
					for ( Map.Entry<Integer, Integer> entry : nbr_val.entrySet() )
					{
						nbr_val.put( entry.getKey(), Integer.parseInt( nbr_data_table.get( 0 ).get( j ) ) );
						j++;
					}

					log_area.append( "Neighbor link changed. Updating DV.\n" );
					// Update the main DV row and report if any changes were made
					if (main_dv_change()) 
					{
						refresh_table();
						// Log for changes
						log_area.append("Updated Primary DT using Bellman-Ford.\n");
	
						// Broadcast changes after updating main DV table
						for (Map.Entry<Integer, Integer> entry : nbr_val.entrySet()) 
						{
							try {
								broadcast_dt();
								send_dt_node( BASE_PORT );
								log_area.append("Sent updated DT to master\n");
							} catch (UnknownHostException e1) {
								// TODO Auto-generated catch block
								e1.printStackTrace();
							} catch (IOException e1) {
								// TODO Auto-generated catch block
								e1.printStackTrace();
							} catch (InterruptedException e1) {
								// TODO Auto-generated catch block
								e1.printStackTrace();
							}
						}
					} 				
				}
			}
		});
	}

	// Overriding Run method
	@Override
	public void run() 
	{

		setup_gui();
		// Listening for any changes in the table and update values
		listen_data_change();

		Timer timer = new Timer();
		timer.schedule( new TimerTask()
		{
			@Override
			public void run() 
			{
				try 
				{
					broadcast_dt();
				} catch (UnknownHostException e) 
				{
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) 
				{
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (InterruptedException e) 
				{
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}, 3000, 3000 );

		

		// try {
		// 	send_and_receive();
		// } catch (UnknownHostException e) {
		// 	// TODO Auto-generated catch block
		// 	e.printStackTrace();
		// } catch (IOException e) {
		// 	// TODO Auto-generated catch block
		// 	e.printStackTrace();
		// } catch (InterruptedException e) {
		// 	// TODO Auto-generated catch block
		// 	e.printStackTrace();
		// }

		try 
		{
			send_and_receive();
			//receive_and_send();
		} 
		catch (IOException | InterruptedException e) 
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	public static void main( String[] args )
	{

    }
}