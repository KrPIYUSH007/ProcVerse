# ProcVerse
# ProcVerse 🌌

### Visual Linux Process Universe

ProcVerse is a Linux process visualization and observability project built using Java and JavaFX.

The core idea behind ProcVerse is:

> Represent the Linux operating system as a living galaxy.

Instead of viewing processes as plain terminal text, ProcVerse transforms them into stars, constellations, and eventually full interactive solar systems.

Every Linux process becomes a celestial object:

* Processes = stars
* Parent-child relationships = orbit systems
* CPU usage = brightness
* Memory usage = mass/size
* Process states = star color

The project combines:

* Linux internals
* process monitoring
* systems programming
* data visualization
* JavaFX graphics
* observability concepts

---

# Current Project Status

## Current Working Features

### Linux Process Parsing

ProcVerse currently reads live Linux process information from:

```bash
/proc
```

The project scans numeric PID directories and extracts:

* Process Name
* Process State
* Memory Usage
* PID
* PPID (partially implemented earlier)
* CPU runtime concepts (implemented previously in terminal version)

---

### Terminal Process Monitor

Before JavaFX visualization, ProcVerse had a fully working terminal-based Linux process monitor that:

* displayed process lists
* sorted by memory
* colorized process states
* refreshed in realtime
* rendered process trees recursively

This phase taught:

* `/proc` filesystem parsing
* Linux process hierarchy
* recursion
* PPID relationships
* process states

---

### JavaFX Universe Renderer

Current visualization version:

* opens JavaFX graphical window
* renders Linux processes as stars
* black-space universe background
* realtime process-generated starfield

---

### Star Properties

Current star mapping:

| Linux Concept | Visual Representation |
| ------------- | --------------------- |
| Process       | Star                  |
| Memory Usage  | Star Radius           |
| Process State | Star Color            |

---

### Current Process State Colors

| State          | Color  |
| -------------- | ------ |
| Running (`R`)  | Lime   |
| Sleeping (`S`) | Cyan   |
| Zombie (`Z`)   | Red    |
| Stopped (`T`)  | Orange |
| Other          | White  |

---

### Current Architecture

Project currently uses:

```text
ProcessInfo.java
    ↓
ProcessMonitor.java
    ↓
Universe.java
```

---

# File Explanations

## ProcessInfo.java

Purpose:

* stores process metadata
* acts as process model object

Current fields:

* pid
* ppid
* name
* state
* memory
* cpuTime

---

## ProcessMonitor.java

Purpose:

* Linux backend engine
* parses `/proc`
* converts Linux process data into Java objects

Responsibilities:

* scan `/proc`
* detect numeric PID folders
* parse `/proc/[pid]/status`
* extract process information
* create `ProcessInfo` objects

This is the core Linux systems component.

---

## Universe.java

Purpose:

* visualization engine
* renders stars using JavaFX

Responsibilities:

* create JavaFX scene
* render process stars
* assign random coordinates
* scale stars using memory
* color stars using process state

Current visualization is intentionally primitive and acts as foundation for future galaxy systems.

---

# Technologies Used

* Java
* JavaFX
* Linux `/proc` filesystem
* Ubuntu VM
* Git
* GitHub

---

# Linux Concepts Learned/Implemented

This project currently demonstrates understanding of:

* Linux process model
* `/proc` filesystem
* PID and PPID hierarchy
* process states
* memory monitoring
* realtime monitoring
* process trees
* recursive traversal
* systems visualization
* observability concepts

---

# Current Problems / Limitations

## 1. Random Star Placement

Currently all stars are randomly positioned.

This causes:

* visual clutter
* overlapping stars
* no meaningful hierarchy placement

Future versions should position processes using:

* PPID hierarchy
* force-directed graphs
* orbital systems

---

## 2. No Camera Controls

Current universe is static.

Missing:

* zoom
* pan
* galaxy navigation

---

## 3. No Process Labels

Stars currently do not show:

* process names
* PID labels
* metadata

Planned feature:

* hover labels
* click interaction
* side info panels

---

## 4. No Parent-Child Visualization

PPID relationships are not visually represented yet.

Planned:

* orbit systems
* connection lines
* process constellations

---

## 5. No Animation

Universe is static.

Future goals:

* drifting stars
* orbital motion
* process spawning animations
* process death animations

---

## 6. No Realtime Updates Yet

Current renderer loads process snapshot only once.

Future:

* live refresh engine
* dynamic process creation/removal

---

# Final Vision / Long-Term Goal

Ultimate ProcVerse goal:

Create a fully interactive Linux process universe where:

* the operating system behaves like a galaxy
* processes orbit parent processes
* clusters form naturally
* users can explore Linux visually

---

# Planned Final Features

## Interactive Galaxy Navigation

* zooming
* panning
* galaxy exploration

---

## Process Interaction

Clicking stars should show:

* process name
* PID
* CPU usage
* memory usage
* parent process
* thread count

---

## Parent-Child Orbit Systems

Parent processes become stars/suns.

Child processes orbit around them like planets.

Example:

```text
☀️ systemd
   🌍 bash
      🪐 java
      🌑 nano
```

---

## Dynamic Realtime Universe

Universe should update continuously:

* new stars appear
* dead processes disappear
* stars change brightness with CPU usage

---

## CPU-Based Brightness

High CPU usage should:

* glow brighter
* pulse
* animate faster

---

## Memory-Based Gravity

Large memory processes should:

* become larger stars
* attract surrounding child processes

---

## Clustered Process Systems

Process groups should form regions:

* browsers
* Java apps
* system daemons
* network services

---

## Hover + Selection UI

Future interaction:

* hover highlights
* star selection
* process inspector panel

---

## Search System

Search for:

* process name
* PID
* high-memory processes

---

## Process Control

Potential future feature:

* kill processes from ProcVerse
* send Linux signals visually

---

## Security / Observability Features

Potential advanced features:

* suspicious process detection
* zombie process alerts
* CPU anomaly visualization

---

## Network Visualization

Future possibility:

* network sockets as connections
* process communication maps

---

## Thread Visualization

Threads could become:

* moons orbiting processes

---

# Important Design Philosophy

ProcVerse is NOT intended to become:

* another terminal monitor
* another htop clone

The project goal is:

> make Linux internals visually explorable.

The focus is:

* systems understanding
* observability
* visualization
* operating system architecture

---

# Current Development Direction

Immediate next recommended steps:

1. clickable stars
2. process labels
3. parent-child connection lines
4. zoom + pan
5. realtime updates
6. orbit systems
7. clustering

---

# Known Design Ideas

Possible future visual mappings:

| Linux Concept       | Visual Concept  |
| ------------------- | --------------- |
| Process             | Star            |
| Parent Process      | Gravity Center  |
| Child Process       | Orbiting Planet |
| CPU Usage           | Brightness      |
| Memory Usage        | Mass            |
| Zombie Process      | Dying Red Star  |
| Threads             | Moons           |
| Network Connections | Energy Beams    |

---

# Performance Concerns

As process count increases:

* rendering all processes equally becomes cluttered

Future solutions:

* clustering
* level-of-detail rendering
* filtering top processes
* dynamic loading

---

# Potential Future Technologies

Possible future upgrades:

* JavaFX animation engine
* OpenGL/LWJGL
* Graph rendering algorithms
* force-directed layouts
* physics simulation
* WebSocket monitoring backend

---

# Why This Project Exists

ProcVerse exists to:

* learn Linux deeply
* understand process systems visually
* build an impressive systems project
* merge operating systems with graphics programming

The project intentionally combines:

* Linux internals
* observability engineering
* visualization
* interactive graphics

into one ecosystem.

---

# Repository Notes

This project is actively evolving.

Architecture and rendering systems will likely change significantly as ProcVerse evolves into a more advanced observability platform.

The current version is considered:

> foundation stage / prototype universe renderer.

---

# Current Achievement Summary

At current stage, ProcVerse already:

* parses live Linux processes
* visualizes operating system data
* renders process galaxies
* uses JavaFX graphics
* demonstrates Linux systems knowledge
* demonstrates realtime visualization concepts

This project has already moved far beyond a typical student CLI project.

---

# Future Dream

Final ProcVerse vision:

An explorable, animated operating-system galaxy where:

* Linux processes become celestial systems
* users navigate the OS visually
* observability becomes intuitive
* systems engineering feels like space exploration

🌌
