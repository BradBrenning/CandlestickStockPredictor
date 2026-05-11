File Directory root/data/* is used for storing raw data and logic for interacting with any associated databases.
and with the application itself.

The following explains the raw data that is stored inside this directory
Filename - description of the files. Sometimes on new line and tabed over for readability
\tab a small description explaining some of the following features in said file. Not always used
\tab the feature name - explaining the use of said feature (Max amount of bytes for storing said feature information)
	

products.dat - Holds information on all known stocks we can access data from
	The following is data pulled from the Coinbase API
	product_id 	- The name of the tradeable product (3 bytes)
	high 		- The highest value the product hit in the last 30 days (10 bytes)
	low 		- The lowest value the product hit in the last 30 days (10 bytes)
	volume_30day 	- The amount of trades that occurred in the last 30 days (12 bytes)
	service_API 	- The API used for trading said stock (3 bytes)
	is_tradeable 	- If the stock is tradable on said API (1 byte)
	
	The following data is determined by the system or user
	should_test 	- statistical analysis saying yes this product should be used in neural network (1 byte)
	overridden 	- The users recommendation for overriding computers decision (1 byte)

	The following data is used for reference to the data used for training
	hist_start 	- Unix time for oldest candlestick we got historical data on product (13 bytes)
	hist_end 	- Unix time for newest candlestick we got historical data on product (13 bytes)

	modified	- Unix time for the last time the product was modified (13 bytes)


candlesticks/model_{product_id}.dat - Holds all the 1-minute candlestick intervals for said product
				      Should only exists if should_test or overridden is XOR logic true
	All data is pulled directory from associated API
	timestamp 	- UNIX time for start of 1-minute interval for following features (13 bytes)
	low		- The lowest value of product during interval (10 bytes)
	high		- The highest value of product during interval (10 bytes)
	open		- The opening value of product at timestamp (10 bytes)
	close		- The closing value of product after 1-minute (10 bytes)
	volume		- The amount of trades that occurred during 1-minute interval (12 bytes)


neuralnetworks/{product_trained_on}.dat - This file contains the neural network object for reloading after shutdown.

trades/userLogs.txt - Holds all trades made by the user for a portfolio monitoring system.

logs/candlestick-{timestamp}.log - Used for debugging purposes for each new launch of the application