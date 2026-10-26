# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.4.1)

project(wavesynth)

# Align native libraries for devices and stores that use 16 KB pages.
add_link_options("-Wl,-z,max-page-size=16384")

# Set up oboe before using it
set(OBOE_DIR ./oboe)
add_subdirectory(${OBOE_DIR} ./oboe)

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.

add_library( # Sets the name of the library.
             wavesynth-lib

             # Sets the library as a shared library.
             SHARED

             # Provides a relative path to your source file(s).
        wavesynth-lib.cpp
        YawnEngine.cpp
        ./src/AD.cpp
        ./src/ADSR.cpp)

# Portable YAWN DSP subset (instruments, MIDI effects, audio effects).
add_library(yawn_dsp STATIC
        yawn_dsp/src/instruments/WavetableSynth.cpp
        yawn_dsp/src/instruments/SubtractiveSynth.cpp
        yawn_dsp/src/effects/Delay.cpp
        yawn_dsp/src/effects/Chorus.cpp
        yawn_dsp/src/effects/Reverb.cpp
        yawn_dsp/src/effects/PlateReverb.cpp
        yawn_dsp/src/effects/Filter.cpp
        yawn_dsp/src/effects/Distortion.cpp
        yawn_dsp/src/effects/EQ.cpp
        yawn_dsp/src/effects/Compressor.cpp
        yawn_dsp/src/effects/Limiter.cpp
        yawn_dsp/src/midi/Arpeggiator.cpp
        yawn_dsp/src/midi/Chord.cpp
        yawn_dsp/src/midi/Scale.cpp
        yawn_dsp/src/midi/NoteLength.cpp
        yawn_dsp/src/midi/VelocityEffect.cpp
        yawn_dsp/src/midi/MidiRandom.cpp
        yawn_dsp/src/midi/MidiPitch.cpp
        yawn_dsp/src/midi/LFO.cpp)

set_target_properties(yawn_dsp PROPERTIES
        CXX_STANDARD 17
        CXX_STANDARD_REQUIRED ON)

target_include_directories(yawn_dsp PUBLIC
        ${CMAKE_CURRENT_SOURCE_DIR}/yawn_dsp/src
        ${CMAKE_CURRENT_SOURCE_DIR}/yawn_dsp/third_party)

# Add include directories
target_include_directories(wavesynth-lib PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/include
        ${CMAKE_CURRENT_SOURCE_DIR}/src
        ${CMAKE_CURRENT_SOURCE_DIR}/yawn_dsp/src
        ${CMAKE_CURRENT_SOURCE_DIR}/yawn_dsp/third_party)

# Searches for a specified prebuilt library and stores the path as a
# variable. Because CMake includes system libraries in the search path by
# default, you only need to specify the name of the public NDK library
# you want to add. CMake verifies that the library exists before
# completing its build.

find_library( # Sets the name of the path variable.
              log-lib

              # Specifies the name of the NDK library that
              # you want CMake to locate.
              log )

# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in this
# build script, prebuilt third-party libraries, or system libraries.

target_link_libraries( # Specifies the target library.
                       wavesynth-lib
                        oboe
                        yawn_dsp
                       # Links the target library to the log library
                       # included in the NDK.
                       ${log-lib} )


include_directories(${OBOE_DIR}/include include)