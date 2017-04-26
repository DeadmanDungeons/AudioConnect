# AudioConnect

A [Spigot](https://www.spigotmc.org/wiki/about-spigot/) Minecraft server plugin that provides a powerful yet simple audio engine to integrate real-time dynamic audio through a hosted web client at [minecraftaudio.com](https://minecraftaudio.com)

Play music, sound-effects, soundscapes, voice-over, or any kind of audio to your players which is dynamic to their location and actions in-game.  You can control where and when the audio plays in-game using its powerful [WorldGuard](https://github.com/sk89q/WorldGuard) region integration.


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
* Promote your server by publishing it on the public server list at [minecraftaudio.com/connect](https://minecraftaudio.com/connect)


## Usage

**Please see the Spigot resource page for setup and usage instructions**
