package com.volmit.adapt.util;

import com.volmit.adapt.Adapt;

public abstract class Looper extends Thread
{
	public void run()
	{
		while(!interrupted())
		{
			try
			{
				long m = loop();

				if(m < 0)
				{
					break;
				}

				Thread.sleep(m);
			}

			catch(InterruptedException e)
			{
				break;
			}

			catch(Throwable e)
			{
				e.printStackTrace();
			}
		}

		Adapt.info("Thread " + getName() + " Shutdown.");
	}

	protected abstract long loop();
}
