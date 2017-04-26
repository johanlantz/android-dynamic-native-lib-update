# android-dynamic-native-lib-update

Experiment on downloading new versions of shared native (C/C++) libraries in Android and using them on the fly. This allows updating native code packaged in a .so without going through a full release cycle of the app. 

It is up to the app to decide how to get the information about the update (polling, push notification etc). A config could for instance contain something like this:

```
{
	"dynlib_cfg": [{
			"min_version": "2.0",
			"max_version": "2.2",
			"armeabi_path": "https://domain.com/armeabi/libmylib_v2.so",
			"armv7a_path": "https://domain.com/amrv7a/libmylib_v2.so",
			"x86_path": "https://domain.com/x86/libmylib_v2.so",
			"md5": "fec58a13bc71752d7a878a7177525fe6",
			"force_restart": true
		},
		{
			"min_version": "2.3",
			"max_version": "3.0",
			"armeabi_path": "https://domain.com/armeabi/libmylib_v3.so",
			"armv7a_path": "https://domain.com/armv7a/libmylib_v3.so",
			"x86_path": "https://domain.com/x86/libmylib_v3.so",
			"md5": "fec58a13bc71752d7a878a7177525fe6",
			"force_restart": false
		}
	]
}
```
Then the app simply has to call the loadDynamicLibrary(...) function providing the name of the bundled lib as well as the name of the wanted updated lib. If the dynamically downloaded version is available, it is choosen first.

Then at a convenient time, for instance when on Wifi and not doing anything important. The app can call downloadUpdatedLibraryIfNeeded(...) with the parameter provided in the config to fetch a new version of the .so (if needed).

The example supports multiple parallell downloads, MD5 checking and always falls back on the bundled native lib in case of problems. 

ABI compatibility for the native code between versions is assumed. Remove the reference to Context and it will be plain Java.
