import java.io.Serializable;
import java.util.HashMap;

public class DVRow implements Serializable
{
	// Variables
	private static final long serialVersionUID = 1L;
	private int DVRow_id;
	private HashMap<Integer, Integer> dvr_table;
	
	// Constructor
	DVRow( int id, HashMap<Integer, Integer> dvr )
	{
		DVRow_id = id;
		dvr_table = dvr;
	}
	
	// Getters
	public int get_id() { return DVRow_id; }
	public HashMap<Integer, Integer> get_dvr() { return dvr_table; }
	
	// Setters
	public void set_dvr( HashMap<Integer, Integer> dvr ) { dvr_table = dvr; }
}
