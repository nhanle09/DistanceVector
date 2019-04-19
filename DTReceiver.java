import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Vector;

public class DTReceiver implements Runnable 
{
    Vector<DTNode> dt_queue;
    int port;
    static final int BASE_PORT = 5680;

    DTReceiver(int dt_port) 
    {
        dt_queue = new Vector<DTNode>();
        port = dt_port;
    }
    // Getters
    public Vector<DTNode> get_queue()
    {
        return dt_queue;
    }

    @Override
    public void run() 
    {
        ServerSocket server_socket;
        try 
        {
            server_socket = new ServerSocket(port);
        } catch (IOException e) 
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        Socket socket = new Socket();

        while ( true )
        {
            socket = server_socket.accept();
            ObjectInputStream obj_instream = new ObjectInputStream ( socket.getInputStream() );
            try 
            {
				dt_queue.add( (DTNode) obj_instream.readObject() );
			} catch (ClassNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        }
    }

}