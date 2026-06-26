You are not just a coding assistant now.

Act as a technical content producer for this repository.

Your task is to help me turn this project into visible engineering content, not just code.

Repository context:

This is a Java Swing prototype inspired by Bookmap-like market visualization.

The goal is not to build a trading product.

The goal is to explore how far plain Java Swing can be pushed for real-time visualization, rendering performance, buffering strategies and profiling.

I want to create public content from this project:

* a strong README
* good screenshots
* a short demo video plan
* a LinkedIn / GitHub post
* a longer engineering article outline
* a list of concrete things I should capture while running the app

Important:

Do not invent features.

Inspect the repository first.

Look at the code, UI, configuration, comments, and existing files.

Then produce a practical content plan.

---

# Deliverables

Create or update a file:

```text
docs/content-plan.md
```

The file should contain the following sections.

---

## 1. Project story

Explain the project in 5-7 sentences.

Focus on:

* why I built it;
* what question I was trying to answer;
* why Java Swing is interesting here;
* why this is an engineering experiment, not a trading product.

Tone: honest, technical, not marketing.

---

## 2. What to show

List the most interesting things visible in the application.

Examples:

* heatmap rendering;
* order book panel;
* CVD / volume panel;
* debug window;
* profiler metrics;
* FPS;
* circular buffer mode;
* historical replay mode;
* different load levels.

Use only things that really exist in the project.

---

## 3. Screenshots to capture

Give me a checklist of screenshots I should create.

For each screenshot specify:

* file name;
* what should be visible;
* what mode/load to use;
* what metric or idea this screenshot demonstrates.

Example format:

```text
screenshots/01-main-ui.png
Show the main heatmap window with order book and volume panel.
Purpose: demonstrates the overall UI and market visualization layout.
```

---

## 4. Demo video plan

Create a 60-90 second screen recording plan.

Break it into scenes.

For each scene specify:

* duration;
* what I should show;
* what I should say or write as caption;
* which hotkeys or modes to use if relevant.

The video should be understandable without voice if I later add captions.

---

## 5. README structure

Propose a concise README structure.

The README should include:

* short description;
* screenshot;
* why this project exists;
* features;
* performance snapshot;
* architecture / rendering pipeline;
* how to run;
* what I learned;
* next experiments.

Do not make it too long.

---

## 6. LinkedIn / GitHub post draft

Write one short post draft.

Tone:

* human;
* honest;
* technical;
* no fake hype;
* no “I am excited to announce”;
* no startup language.

The post should explain that I built a Bookmap-like visualization prototype in plain Java Swing to explore rendering performance.

Mention one or two concrete metrics if they are visible or can be measured.

If exact metrics are unclear, leave placeholders like:

```text
[insert FPS here]
[insert number of price levels here]
```

Do not invent numbers.

---

## 7. Engineering article outline

Create an outline for a longer article.

Possible title:

```text
How far can plain Java Swing be pushed for real-time visualization?
```

The outline should cover:

* motivation;
* first implementation;
* rendering loop;
* data generation / replay;
* buffer strategy;
* profiler;
* what became the bottleneck;
* what I would try next.

---

## 8. Missing information

At the end, list questions you need me to answer before writing the final README or article.

Examples:

* What machine was used for measurements?
* What Java version?
* How many events per bucket?
* What is the highest stable FPS?
* Which mode should be considered the main demo mode?

Keep the questions concrete.

---

# Style rules

* Be concise.
* Be practical.
* Do not document every class.
* Do not produce generic content.
* Do not oversell the project.
* Do not call it production-ready.
* Do not call it a clone.
* Prefer "prototype", "experiment", "playground", "investigation".
* Use plain engineering language.

```
```
