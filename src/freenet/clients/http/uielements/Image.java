package freenet.clients.http.uielements;

/**
 * Creates an image element
 */
public class Image extends OutputNode {

	private String alt;
	private int height;
	private String src;
	private String title;
	private int width;

	public Image(String src, String alt, String title, int height, int width) {
		this(src, alt, title);
		this.setSize(height, width);
	}

	public Image(String src, String alt, String title) {
		this(src, alt);
		this.setTitle(title);
	}

	public Image(String src, String alt) {
		super("img");
		this.setSrc(src);
		this.setAlt(alt);
	}

	private void setAlt(String alt) {
		this.alt = alt;
		this.addAttribute("alt", alt);
	}
	private void setSize(int height, int width) {
		this.height = height;
		this.width = width;
		this.addAttributes(new String[]{"height", "width"},
			new String[]{Integer.toString(height), Integer.toString(width)});
	}
	private void setSrc(String src) {
		this.src = src;
		this.addAttribute("src", src);
	}
	private void setTitle(String title) {
		this.title = title;
		this.addAttribute("title", title);
	}
}
