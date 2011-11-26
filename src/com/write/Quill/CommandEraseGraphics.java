package com.write.Quill;

import name.vbraun.view.write.Graphics;
import name.vbraun.view.write.Page;

public class CommandEraseGraphics extends Command {

	protected final Graphics graphics;
	
	public CommandEraseGraphics(Page page, Graphics toAdd) {
		super(page);
		graphics = toAdd;
	}

	@Override
	public void execute() {
		UndoManager.getApplication().remove(getPage(), graphics);
	}

	@Override
	public void revert() {
		UndoManager.getApplication().add(getPage(), graphics);
	}
	
	@Override
	public String toString() {
		int n = Book.getBook().getPageNumber(getPage());
		return "Remove pen stroke on page "+n;
	}

}
