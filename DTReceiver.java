import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;

public class DTReceiver implements Runnable 
{
    private Queue<DTNode> dt_queue;
    private int port;
    private HashSet<HashMap<Integer, Integer>> mgt_queue;

    DTReceiver(int dt_port) 
    {
        mgt_queue = new HashSet<HashMap<Integer, Integer>>();
        dt_queue = new LinkedList<>();
        port = dt_port;
    }
    // Getters
    public Queue<DTNode> get_queue() { return dt_queue; }

    // Constantly receiving data and store into a Queue
    public void receive_data() throws IOException, ClassNotFoundException, InterruptedException
    {
        // Socket variables
        ServerSocket server_socket = new ServerSocket( port );
        Socket socket = new Socket();
        while ( true )
        {
            socket = server_socket.accept();
            ObjectInputStream obj_instream = new ObjectInputStream ( socket.getInputStream() );

            DTNode received_dt = ( DTNode ) obj_instream.readObject();

            if( !mgt_queue.contains( received_dt.get_dt() ) )
            {
                mgt_queue.add( received_dt.get_dt() );
                dt_queue.add( received_dt );
            }
            
            //dt_queue.add( ( DTNode ) obj_instream.readObject() );
        }
    }

    @Override
    public void run() 
    {
        try {
			receive_data();
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

}