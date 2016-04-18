import java.util.*;
import java.io.*;
import javax.imageio.*;
import java.awt.Rectangle;
import java.awt.Graphics2D;
import java.awt.image.*;

public class AtlasGenerator {
    public static void main(String args[]) {
        if (args.length < 4) {
            System.out.println("Texture Atlas Generator by Lukasz Bruun - lukasz.dk");
            System.out.println("\tUsage: AtlasGenerator <name> <width> <height> <padding> <directory> [<directory> ...]");
            System.out.println("\t\t<padding>: Padding between images in the final texture atlas.");
            System.out.println("\tExample: AtlasGenerator atlas 2048 2048 5 images");
            return;
        }

        AtlasGenerator atlasGenerator = new AtlasGenerator();
        List<File> dirs = new ArrayList<File>();
        for (int i = 4; i < args.length; ++i) {
            dirs.add(new File(args[i]));
        }
        atlasGenerator.run(args[0], Integer.parseInt(args[1]), Integer.parseInt(args[2]), Integer.parseInt(args[3]), dirs);
    }

    public void run(String name, int width, int height, int padding, List<File> dirs) {
        List<File> imageFiles = new ArrayList<File>();

        for (File file : dirs) {
            if (!file.exists() || !file.isDirectory()) {
                System.out.println("Error: Could not find directory '" + file.getPath() + "'");
                return;
            }

            getImageFiles(file, imageFiles);
        }

        System.out.println("Found " + imageFiles.size() + " images");

        Set<ImageName> imageNameSet = new TreeSet<ImageName>(new ImageNameComparator());

        for (File f : imageFiles) {
            try {
                BufferedImage image = ImageIO.read(f);

                if (image.getWidth() > width || image.getHeight() > height) {
                    System.out.println("Error: '" + f.getPath() + "' (" + image.getWidth() + "x" + image.getHeight()
                            + ") is larger than the atlas (" + width + "x" + height + ")");
                    return;
                }

                String path = f.getPath().substring(0, f.getPath().lastIndexOf(".")).replace("\\", "/");

                imageNameSet.add(new ImageName(image, path));

            } catch (IOException e) {
                System.out.println("Could not open file: '" + f.getAbsoluteFile() + "'");
            }
        }

        List<Texture> textures = new ArrayList<Texture>();

        textures.add(new Texture(width, height, padding));

        int count = 0;

        for (ImageName imageName : imageNameSet) {
            boolean added = false;

            System.out.println("Adding " + imageName.name + " to atlas (" + (++count) + ")");

            for (Texture texture : textures) {
                if (texture.addImage(imageName.image, imageName.name, padding)) {
                    added = true;
                    break;
                }
            }

            if (!added) {
                Texture texture = new Texture(width, height, padding);
                texture.addImage(imageName.image, imageName.name, padding);
                textures.add(texture);
            }
        }

        count = 0;

        for (Texture texture : textures) {
            System.out.println("Writing atlas: " + name + (++count));
            texture.write(name + count, width, height);
        }
    }

    private void getImageFiles(File file, List<File> imageFiles) {
        if (file.isDirectory()) {
            File[] files = file.listFiles(new ImageFilenameFilter());
            File[] directories = file.listFiles(new DirectoryFileFilter());

            imageFiles.addAll(Arrays.asList(files));

            for (File d : directories) {
                getImageFiles(d, imageFiles);
            }
        }
    }

    private class ImageName {
        public BufferedImage image;
        public String name;

        public ImageName(BufferedImage image, String name) {
            this.image = image;
            this.name = name;
        }
    }

    private class ImageNameComparator implements Comparator<ImageName> {
        @Override
        public int compare(ImageName image1, ImageName image2) {
            int area1 = image1.image.getWidth() * image1.image.getHeight();
            int area2 = image2.image.getWidth() * image2.image.getHeight();

            if (area1 != area2) {
                return area2 - area1;
            } else {
                return image1.name.compareTo(image2.name);
            }
        }
    }

    private class ImageFilenameFilter implements FilenameFilter {
        @Override
        public boolean accept(File dir, String name) {
            String lcName = name.toLowerCase(Locale.ROOT);
            return lcName.endsWith(".png") || lcName.endsWith(".gif") || lcName.endsWith(".jpg") || lcName.endsWith(".jpeg");
        }
    }

    private class DirectoryFileFilter implements FileFilter {
        @Override
        public boolean accept(File pathname) {
            return pathname.isDirectory();
        }
    }

    public class Texture {
        private class Node {
            public Rectangle rect;
            public Node child[];
            public BufferedImage image;

            public Node(int x, int y, int width, int height) {
                rect = new Rectangle(x, y, width, height);
                child = new Node[2];
                child[0] = null;
                child[1] = null;
                image = null;
            }

            public boolean IsLeaf() {
                return child[0] == null && child[1] == null;
            }

            // Algorithm from http://www.blackpawn.com/texts/lightmaps/
            public Node insert(BufferedImage image, int padding) {
                if (!IsLeaf()) {
                    Node newNode = child[0].insert(image, padding);

                    if (newNode != null) {
                        return newNode;
                    }

                    return child[1].insert(image, padding);
                } else {
                    if (this.image != null) {
                        return null; // occupied
                    }

                    if (image.getWidth() > rect.width || image.getHeight() > rect.height) {
                        return null; // does not fit
                    }

                    if (image.getWidth() == rect.width && image.getHeight() == rect.height) {
                        this.image = image; // perfect fit
                        return this;
                    }

                    int dw = rect.width - image.getWidth();
                    int dh = rect.height - image.getHeight();

                    if (dw > dh) {
                        child[0] = new Node(rect.x, rect.y, image.getWidth(), rect.height);
                        child[1] = new Node(padding + rect.x + image.getWidth(), rect.y,
                                rect.width - image.getWidth() - padding, rect.height);
                    } else {
                        child[0] = new Node(rect.x, rect.y, rect.width, image.getHeight());
                        child[1] = new Node(rect.x, padding + rect.y + image.getHeight(), rect.width,
                                rect.height - image.getHeight() - padding);
                    }
                    /*
                     * if(dw > dh) { child[0] = new Node(rect.x, rect.y,
                     * image.getWidth(), rect.height); child[1] = new
                     * Node(padding+rect.x+image.getWidth(), rect.y, rect.width
                     * - image.getWidth(), rect.height); } else { child[0] = new
                     * Node(rect.x, rect.y, rect.width, image.getHeight());
                     * child[1] = new Node(rect.x,
                     * padding+rect.y+image.getHeight(), rect.width, rect.height
                     * - image.getHeight()); }
                     */

                    return child[0].insert(image, padding);
                }
            }
        }

        private BufferedImage image;
        private Graphics2D graphics;
        private Node root;
        private Map<String, Rectangle> rectangleMap;

        public Texture(int width, int height, int padding) {
            image = new BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR);
            graphics = image.createGraphics();

            root = new Node(padding, padding, width, height);
            rectangleMap = new TreeMap<String, Rectangle>();
        }

        public boolean addImage(BufferedImage image, String name, int padding) {
            Node node = root.insert(image, padding);

            if (node == null) {
                return false;
            }

            rectangleMap.put(name, node.rect);
            graphics.drawImage(image, null, node.rect.x, node.rect.y);

            return true;
        }

        public void write(String name, int width, int height) {
            try {
                ImageIO.write(image, "png", new File(name + ".png"));
                
                try (BufferedWriter atlas = new BufferedWriter(new FileWriter(name + ".txt"))) {
                    for (Map.Entry<String, Rectangle> e : rectangleMap.entrySet()) {
                        Rectangle r = e.getValue();
                        String keyVal = e.getKey();
                        atlas.write(keyVal + " " + name + ".png(" + r.x + "," + r.y + "-" + r.width + "," + r.height + ")");
                        atlas.newLine();
                    }
                }
            } catch (IOException e) {
                System.err.println("File writing for " + name + ".png failed");
                e.printStackTrace();
            }
        }
    }
}
