package name.vbraun.view.write;

public class Paper {
	private static final String TAG = "PaperType";

	public enum Type {
		EMPTY, RULED, QUAD, HEX, COLLEGERULED, NARROWRULED, CORNELLNOTES
	}
		
	private CharSequence name;
	private Type type;
	
	public static final Paper[] Table = {
		new Paper("Blank",  Type.EMPTY),
		new Paper("Legal ruled",  Type.RULED),
		new Paper("College ruled",  Type.COLLEGERULED),
		new Paper("Narrow ruled",  Type.NARROWRULED),
		new Paper("Quad paper",  Type.QUAD),
		new Paper("Cornell Notes",  Type.CORNELLNOTES)
	};

		
	public Paper(CharSequence n, Paper.Type t) { 
		name = n; 
		type = t;
	}
		
	public CharSequence getName() {
		return name;
	}

	public Type getValue() {
		return type;
	}
		

}
