# Example Project

## Configuration

To get the example up and running you just need your configuration file. Get yours at [support@contentpass.de](support@contentpass.de).

Replace the dummy `contentpass_configuration.json` in `res/raw` with your configuration file and you're good to go.


## Notes

The `ExampleViewModel` handles all configuration of and communication with the `ContentPass` object. Refer to that class on how to interact with the sdk.

In general you should of course inject the `ContentPass` object into your `ViewModel` classes and not instantiate it there. 