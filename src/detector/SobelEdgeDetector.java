package detector;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.imageio.*;

public class SobelEdgeDetector
{
	private static volatile int counter;

	private static synchronized void increaseCounter()
	{
		counter++;
	}

	public static void main(String[] args) throws IOException, InterruptedException
	{
		int numArgs = 0;
		String directory = System.getProperty("user.home") + "/Edge-detection/finished/";
		boolean hasThreshold = false;
		int threshold = 0;
		boolean isBinary = false;

		boolean hasntHitFiles = true;
		for(; hasntHitFiles;)
		{
			switch(args[numArgs])
			{
				case "-l":
				{
					directory = args[numArgs + 1];
					numArgs += 2;
					break;
				}
				case "-t":
				{
					hasThreshold = true;
					threshold = new Integer(args[numArgs + 1]);
					numArgs += 2;
					break;
				}
				case "-b":
				{
					isBinary = true;
					numArgs += 1;
					break;
				}
				default:
				{
					hasntHitFiles = false;
				}
			}
		}

		// imo clearer than binary^hasThreshold - and also probably a little bit faster since it only needs to access 1 variable and then negate
		// as opposed to access two variables and then some operation with them both
		if(isBinary && !hasThreshold)
		{
			System.out.println("Can't be binary without a threshold, so changing it back");
			isBinary = false;
		}

		(new File(directory)).mkdirs();
		System.out.println("Result directory: " + (new File(directory)).getAbsolutePath());

		for(int s = numArgs; s < args.length; s++)
		{
			counter = 0;

			ExecutorService pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

			File input = new File(args[s]);
			System.out.println("Input information: " + input.getAbsolutePath());
			System.out.println("Input exists? " + input.exists());
			final BufferedImage source = ImageIO.read(input);

			final int[][] hx = { {-1, 0, 1}, {-2, 0, 2}, {-1, 0, 1}};
			final int[][] hy = { {-1, -2, -1}, {0, 0, 0}, {1, 2, 1}};

			final float[][][] gx = new float[source.getHeight()][source.getWidth()][3];
			final float[][][] gy = new float[source.getHeight()][source.getWidth()][3];

			final float[][][] g = new float[source.getHeight()][source.getWidth()][3];

			System.out.println("Screen size: " + source.getWidth() + " X " + source.getHeight());
			System.out.println("Applying the Sobel Edge-Detection formula");

			for(int y = 1; y < source.getHeight() - 1; y++)
			{
				final int yy = y;
				pool.execute(new Runnable()
					{
						public void run()
						{
							for(int x = 1; x < source.getWidth() - 1; x++)
							{
								for(int k = -1; k < 2; k++)
								{
									if(yy - k < 0 || yy - k >= source.getHeight())
									{
										continue;
									}
									for(int j = -1; j < 2; j++)
									{
										if(x - j < 0 || x - j >= source.getWidth())
										{
											continue;
										}
										float[] color = (new Color(source.getRGB(x - j, yy - k))).getColorComponents(null);
										for(int i = 0; i < 3; i++)
										{
											gx[yy][x][i] += hx[k + 1][j + 1] * color[i];
											gy[yy][x][i] += hy[k + 1][j + 1] * color[i];
										}
									}
								}
								for(int i = 0; i < 3; i++)
								{
									g[yy][x][i] = (gx[yy][x][i]) * (gx[yy][x][i]) + (gy[yy][x][i]) * (gy[yy][x][i]);
								}
							}

							increaseCounter();
						}
					});
			}

			pool.shutdown();
			while(!pool.isTerminated())
			{
				Thread.sleep(1000);

				System.out.println("Completed " + counter + " rows out of " + source.getHeight());
			}

			System.out.println("Finding maximum value");

			float maxValue = 0;
			for(int y = 0; y < source.getHeight(); y++)
			{
				for(int x = 0; x < source.getWidth(); x++)
				{
					for(int i = 0; i < 3; i++)
					{
						if(g[y][x][i] > maxValue)
							maxValue = g[y][x][i];
					}
				}
			}

			System.out.println("Normalizing and generating image");

			BufferedImage destination = new BufferedImage(source.getWidth(), source.getHeight(), source.getType());

			for(int y = 0; y < source.getHeight(); y++)
			{
				for(int x = 0; x < source.getWidth(); x++)
				{
					boolean isSignificant = false;
					for(int i = 0; i < 3; i++)
					{
						g[y][x][i] = g[y][x][i] / maxValue * 255;
						if(g[y][x][i] > threshold)
							isSignificant = true;
					}

					if(hasThreshold)
					{
						if(isSignificant)
						{
							if(isBinary)
							{
								g[y][x][0] = g[y][x][1] = g[y][x][2] = 255;
							}
							else
							{

								float max = g[y][x][0];
								if(max < g[y][x][1])
									max = g[y][x][1];
								if(max < g[y][x][2])
									max = g[y][x][2];

								g[y][x][0] /= max;
								g[y][x][1] /= max;
								g[y][x][2] /= max;

								g[y][x][0] *= 255;
								g[y][x][1] *= 255;
								g[y][x][2] *= 255;
							}
						}
						else if(isBinary)
						{
							g[y][x][0] = g[y][x][1] = g[y][x][2] = 0;
						}
					}

					destination.setRGB(x, y, (new Color((int) g[y][x][0], (int) g[y][x][1], (int) g[y][x][2])).getRGB());
				}
			}

			System.out.println("Creating image");
			String name = input.getName();
			ImageIO.write(destination, name.substring(name.lastIndexOf(".") + 1),
			          new File(directory + name.substring(0, name.lastIndexOf(".")) + "edge" + name.substring(name.lastIndexOf("."))));

			System.out.println("Done\n");
		}
	}
}
