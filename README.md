# ![AudioConnect](http://i.imgur.com/IdmU2cS.png)

A [Spigot](https://www.spigotmc.org/wiki/about-spigot/) Minecraft server plugin that provides a powerful web-based audio engine to integrate real-time dynamic audio around player location and actions.

Use AudioConnect to provide truly unique and immersive gameplay with music, sound-effects, soundscapes, voice-over, or any kind of audio.
You can control where and when the audio plays in-game using its powerful [WorldGuard](https://github.com/sk89q/WorldGuard) region integration.

This plugin is intended to operate as a producer client to the [minecraftaudio.com](https://minecraftaudio.com) web service, but it can be configured to connect with any web server that follows the protocol.

AudioConnect is open source and is available under The MIT License (MIT)

A release copy of AudioConnect can be obtained from the [Spigot Resource page](https://www.spigotmc.org/resources/audioconnect.40339/)

### Plugin Features:

* Define multiple audio tracks to separate audio with different purposes such as music and sound-effects
  * Layer tracks to play multiple audio sources simultaneously and independently
  * Configure how tracks play its audio sources
    * Play in random or sequential order
    * Play through once or repeatedly
    * Play with or without fade-in/fade-out transitions
* Use WorldGuard region flags and settings to control location based audio
  * Add constraints to limit when an audio source can be played based on the in-game world time (night, morning, afternoon, etc.)
  * Delay the transition between audio sources by a constant or random duration
  * Flag a single region to play through multiple audio sources on multiple independent tracks
  * Combine or prioritize audio settings of overlapping regions
* Players can mute/unmute the audio using in-game commands
* Use commands or the API to play audio sources to all or specific players
* Use commands to monitor which players are connected and listening


## Links

* [Homepage](https://minecraftaudio.com)
* [Spigot Resource](https://www.spigotmc.org/resources/audioconnect.40339/)
* [Wiki](https://github.com/DeadmanDungeons/AudioConnect/wiki)
* [Javadocs](https://deadmandungeons.github.io/AudioConnect/)
