package com.write.Quill;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;


public class ExportActivity 
	extends Activity 
	implements OnClickListener, OnItemSelectedListener {

	private static final String TAG = "ExportActivity";
	
	// Tag for the Intent extra data carrying the book
	protected static final String INTENT_EXTRA_BOOK = "Quill_Book"; 
	
	private View layout;
	private Book book;
	private Page page;
	private Handler handler = new Handler();
	private ProgressBar progressBar;
	private String filename;
	private Button exportButton;
	private PDFExporter pdfExporter;
	private Thread exportThread;
	private String fullFilename;
	private File file;
	private Spinner sizes;
	private ArrayAdapter<CharSequence> exportSizes;
	private TextView name;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        book = Book.getBook();
        page = book.currentPage();
        
    	LayoutInflater inflater = getLayoutInflater();
    	
    	layout = inflater.inflate(R.layout.export, null);
    	exportButton = (Button)layout.findViewById(R.id.export_button);
    	exportButton.setOnClickListener(this);
    	Button cancel = (Button)layout.findViewById(R.id.export_cancel);
    	cancel.setOnClickListener(this);

		LinkedList<String> sizes_values = new LinkedList<String>();
    	exportSizes = new ArrayAdapter(this,
    			android.R.layout.simple_spinner_item, sizes_values);
		String [] strings = getResources().getStringArray(R.array.export_size_vector);
		exportSizes.addAll(strings);
    	exportSizes.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    	sizes = (Spinner)layout.findViewById(R.id.export_size);
    	sizes.setAdapter(exportSizes);

    	name = (TextView)layout.findViewById(R.id.export_name);
    	
    	Spinner format = (Spinner)layout.findViewById(R.id.export_file_format);
    	format.setOnItemSelectedListener(this);

    	progressBar = (ProgressBar)layout.findViewById(R.id.export_progress);
    	setContentView(layout);
	}
	
    
    // Somebody clicked on Cancel, Export
	@Override
    public void onClick(View v) {
      	switch (v.getId()) {
    	case R.id.export_button:
    		doExport();
    		return;
    	case R.id.export_cancel:
    		doCancel();
    		return;
    	}
    }

	void changeFileExtensionTo(String ext) {
		String txt = name.getText().toString();
		int dot = txt.lastIndexOf('.');
		if (dot == -1 || dot == 0) 
			txt = txt + ext;
		else
			txt = txt.substring(0, dot) + ext;
		name.setText(txt);
	}	
	
	@Override
	public void onItemSelected(AdapterView<?> spinner, View view, int position,
			long id) {
      	Log.v(TAG, "Format "+position);
      	String[] strings;
      	switch (position) {
		case OUTPUT_FORMAT_PDF:  // PDF format
			sizes.setEnabled(true);
			strings = getResources().getStringArray(R.array.export_size_vector);
			exportSizes.clear();
			exportSizes.addAll(strings);
        	exportSizes.notifyDataSetChanged();
        	changeFileExtensionTo(".pdf");
			return;
		case OUTPUT_FORMAT_PNG:  // Raster image format
			sizes.setEnabled(true);
			strings = getResources().getStringArray(R.array.export_size_raster);
			exportSizes.clear();
			exportSizes.addAll(strings);
        	exportSizes.notifyDataSetChanged();
        	changeFileExtensionTo(".png");
			return;
		case OUTPUT_FORMAT_BACKUP:  // Quill backup archive
			sizes.setEnabled(false);
			exportSizes.clear();
        	exportSizes.notifyDataSetChanged();
        	changeFileExtensionTo(".quill");
			return;
		}		
	}

	@Override
	public void onNothingSelected(AdapterView<?> arg0) {
		Log.v(TAG, "onNothingSelected");
	}
    
	private static final int OUTPUT_FORMAT_PDF = 0;
	private static final int OUTPUT_FORMAT_PNG = 1;
	private static final int OUTPUT_FORMAT_BACKUP = 2;

	
	// somebody changed the Output format
	private void changeExportFileFormat(Spinner format) {
      	Spinner sizes = (Spinner)layout.findViewById(R.id.export_size);
      	Log.v(TAG, "Format "+format.getSelectedItemPosition());
		switch (format.getSelectedItemPosition()) {
		case OUTPUT_FORMAT_PDF:
			sizes.setEnabled(true);
			sizes.setAdapter(new ArrayAdapter<String>(this, R.array.export_size_vector));
			return;
		case OUTPUT_FORMAT_PNG:
			sizes.setEnabled(true);
			sizes.setAdapter(new ArrayAdapter<String>(this, R.array.export_size_raster));
			return;
		case OUTPUT_FORMAT_BACKUP:
			sizes.setEnabled(false);
			return;
		}
	}

    protected void doExport() {
    	Log.v(TAG, "doExport()");
    	if (pdfExporter != null) return;
    	if (!openShareFile()) return;
    	Spinner format = (Spinner)layout.findViewById(R.id.export_file_format);
    	switch (format.getSelectedItemPosition()) {
		case OUTPUT_FORMAT_PDF:
			doExportPdf();
			return;
		case OUTPUT_FORMAT_PNG:
			doExportPng();
			return;
		case OUTPUT_FORMAT_BACKUP:
			doExportArchive();
			return;
    	}
    }
    
    private void doExportArchive() {
    	try {
    		book.saveArchive(file);
    	} catch (IOException e) {
			Log.e(TAG, "Error writing file "+e.toString());
        	Toast.makeText(this, "Unable to write file "+fullFilename, Toast.LENGTH_LONG).show();   		
    	}
    	doShare();
    }

    
    private void doExportPng() {
    	// TODO
    	doShare();
    }
    
    private void doExportPdf() {
		threadLockActivity();
        assert pdfExporter == null : "Trying to run two export threads??";
    	pdfExporter = new PDFExporter();
        exportThread = new Thread(new Runnable() {
            public void run() {
            	pdfExporter.add(page);
            	pdfExporter.export(file);
            	pdfExporter = null;
            }});
        // exportThread.setPriority(Thread.MIN_PRIORITY);
        exportThread.start();
   }
    
    protected void doCancel() {
    	if (pdfExporter == null) {
    		finish();
    		threadUnlockActivity();
    	} else
    		pdfExporter.interrupt();
	}
    
    private static final int SHARE_GENERIC = 0;
    private static final int SHARE_EVERNOTE = 1;
    private static final int SHARE_EXTERNAL = 2;
    private static final int SHARE_INTERNAL = 3;
    private static final int SHARE_USB = 4;

    
    private void doShare() {
      	Spinner spinner = (Spinner)layout.findViewById(R.id.export_via);
    	int pos = spinner.getSelectedItemPosition();
    	switch (pos) {
    	case SHARE_GENERIC:
    		doShareGeneric();
    		return;
    	case SHARE_EVERNOTE:
    		doShareEvernote();
    		return;
    	case SHARE_EXTERNAL:
    	case SHARE_INTERNAL:
    	case SHARE_USB:
        	Toast.makeText(this, getString(R.string.export_saved_as)+" "+fullFilename, 
    				Toast.LENGTH_LONG).show();
        	finish();
		return;
    	}
    }
    
    private boolean openShareFile() {
      	Spinner spinner = (Spinner)layout.findViewById(R.id.export_via);
    	int pos = spinner.getSelectedItemPosition();
		filename = name.getText().toString();
		if (filename.startsWith("/"))
    		file = new File(filename);
		else
			switch (pos) {
			case SHARE_GENERIC:
			case SHARE_EVERNOTE:
				file = new File(getExternalFilesDir(null), filename);
				break;
			case SHARE_INTERNAL:
				// file = new File(getExternalFilesDir(null), filename);
				file = new File("/mnt/sdcard", filename);
				break;
			case SHARE_EXTERNAL:
				file = new File("/mnt/external_sd", filename);
				break;
			case SHARE_USB:
				file = new File("/mnt/usbdrive", filename);
				break;
			}
		try {
			fullFilename = file.getCanonicalPath();
		} catch (IOException e) {
			Log.e(TAG, "Path does not exist: "+e.toString());
        	Toast.makeText(this, "Path does not exist", Toast.LENGTH_LONG).show();
			return false;
		}
		try {
			file.createNewFile();
		} catch(IOException e) {
			Log.e(TAG, "Error creating file "+e.toString());
        	Toast.makeText(this, "Unable to create file "+fullFilename, Toast.LENGTH_LONG).show();
        	return false;
        }
		return true;
    }
    
    private void doShareGeneric() {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_SEND);
        intent.setType("application/pdf");
        intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file));
        try {
            startActivity(Intent.createChooser(intent, 
            		getString(R.string.export_share_title)));
            finish();
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(this, getString(R.string.err_no_way_to_share), Toast.LENGTH_LONG).show();
        }
    	
    }
    
    // Names of Evernote-specific Intent actions and extras
    public static final String ACTION_NEW_NOTE             = "com.evernote.action.CREATE_NEW_NOTE";
    public static final String EXTRA_NOTE_GUID             = "NOTE_GUID";
    public static final String EXTRA_SOURCE_APP            = "SOURCE_APP";
    public static final String EXTRA_AUTHOR                = "AUTHOR";
    public static final String EXTRA_QUICK_SEND            = "QUICK_SEND";
    public static final String EXTRA_TAGS                  = "TAG_NAME_LIST";

    private void doShareEvernote() {
        Intent intent = new Intent();
        intent.setAction(ACTION_NEW_NOTE);
        intent.putExtra(Intent.EXTRA_TITLE, filename);
        // intent.putExtra(Intent.EXTRA_TEXT, text);

        // Add tags, which will be created if they don't exist
        ArrayList<String> tags = new ArrayList<String>();
        tags.add("Quill");
        tags.add("Android");
        intent.putExtra(EXTRA_TAGS, tags);
        
        // If we knew the GUID of a notebook that we wanted to put the new note in, we could set it here
        //String notebookGuid = "d7c41948-f4aa-46e1-a818-e6ff73877145";
        //intent.putExtra(EXTRA_NOTE_GUID, notebookGuid);
        
        // Set the note's author, souceUrl and sourceApplication attributes.
        // To learn more, see
        // http://www.evernote.com/about/developer/api/ref/Types.html#Struct_NoteAttributes
        // intent.putExtra(EXTRA_AUTHOR, "");
        intent.putExtra(EXTRA_SOURCE_APP, "Quill");
        
        // If you set QUICK_SEND to true, Evernote for Android will automatically "save"
        // the new note. The user will see the "New note" activity briefly, then
        // return to your application.
        //	intent.putExtra(EXTRA_QUICK_SEND, true);
        
        // Add file(s) to be attached to the note
        ArrayList<Uri> uriList = new ArrayList<Uri>();
        uriList.add(Uri.fromFile(file));
        intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM , uriList);
        try {
        	startActivity(intent);
        	finish();
        } catch (android.content.ActivityNotFoundException ex) {
        	Toast.makeText(this, getString(R.string.err_evernote_not_found), Toast.LENGTH_LONG).show();
        } 
    }
            
    private void threadLockActivity() {
    	exportButton.setPressed(true);
        handler.post(mUpdateProgress);
    }
    
    private void threadUnlockActivity() {
    	progressBar.setProgress(0);
    	handler.removeCallbacks(mUpdateProgress);
    	exportButton.setPressed(false);
    }
    

    private Runnable mUpdateProgress = new Runnable() {
    	   public void run() {
    		   PDFExporter exporter = pdfExporter;
    		   if (exporter == null) {
    			   threadUnlockActivity();
    			   doShare();
       		   return;
    		   }
    		   exportButton.setPressed(true);
    		   progressBar.setProgress(exporter.get_progress());
    		   progressBar.invalidate();
               handler.postDelayed(mUpdateProgress, 200);
    	   }
    	};
}
