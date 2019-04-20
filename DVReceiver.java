import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;

public class DVReceiver implements Runnable 
{
    // Variables
    private Queue<DVRow> dt_queue;
    private int port;
    private HashSet<HashMap<Integer, Integer>> mgt_queue;

    // Constructors
    DVReceiver(int dt_port) 
    {
        mgt_queue = new HashSet<HashMap<Integer, Integer>>();
        dt_queue = new LinkedList<>();
        port = dt_port;
    }
    // Getters
    public Queue<DVRow> get_queue() { return dt_queue; }

    // Receiving data and store it into a queue
    public void receive_data() throws IOException, ClassNotFoundException, InterruptedException
    {
        // Socket variables
        ServerSocket server_socket = new ServerSocket(port);
        Socket socket = new Socket();
        while ( true )
        {
            // Receiving and casting objects to DVRow
            socket = server_socket.accept();
            ObjectInputStream obj_instream = new ObjectInputStream ( socket.getInputStream() );
            DVRow received_dt = ( DVRow ) obj_instream.readObject();

            // Using HashSet management queue to ignore duplicate DTs by comparing
            // the received HashMap with what existed 
            if( !mgt_queue.contains( received_dt.get_dvr() ) )
            {
                mgt_queue.add( received_dt.get_dvr() );
                dt_queue.add( received_dt );
            }
        }
    }

    @Override
    public void run() 
    {
        try {
			receive_data();
		} catch (ClassNotFoundException e) {
			System.out.println( "DVReceiver: Class Not Found Exception" );
			e.printStackTrace();
		} catch (IOException e) {
			System.out.println( "DVReceiver: IO Exception" );
			e.printStackTrace();
		} catch (InterruptedException e) {
            System.out.println( "DVReceiver: Interrupted Exception" );
            e.printStackTrace();
        }
    }
}