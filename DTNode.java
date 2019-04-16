import java.util.HashMap;
import java.io.Serializable;

public class DTNode implements Serializable
{
	private static final long serialVersionUID = 1L;
	private int DTNode_id;
	private HashMap<Integer, Integer> dt_table;
	
	DTNode( int id, HashMap<Integer, Integer> dt )
	{
		DTNode_id = id;
		dt_table = dt;
	}
	
	public int get_id()
	{
		return DTNode_id;
	}
	
	public void set_dt( HashMap<Integer, Integer> dt )
	{
		dt_table = dt;
	}
	
	public HashMap<Integer, Integer> get_dt()
	{
		return dt_table;
	}
}
