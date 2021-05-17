# Workflow to generate population


## Step 1 Generating population

Initial distribution of population (`population.bin`) is generated using the `PopulationGenerator` tools Object with correct input parameters/data :

{{ code_from_file("../src/main/scala/eighties/h24/generation.scala", 365, 373, "scala") }}

!!! note "Parameters of `eighties.h24.tools.PopulationGenerator`"

    -  `c` shape of Iris,
    -  `p` & `f` population structure and education from census,
    -  `g` population density in raster format,
    -  `s` grid size for population projection
    -  `r` if you want a random population

Given population density we build a matrix of cells.

Next, the function `generatePopulationRandomly` use information about structure of population (age,sex,education) at the census block level (iris) to sample new population sized individuals using direct sampling algorithm.

We don't consider the census block geometries, so we attribute for each individual (`IndividualFeature`) a random cell in a matrix of same size of the population raster grid. This random cell is taken using a multinomial to respect density of population.

Finally, individuals (x,y) are translated/relocated to some new grid of cells function of the `gridSize` parameter.

## Step 2 Move population

Using EGT and population previously generated, flows are precomputed as File (`move.bin`) before running simulation.

In `generation.scala` , in `flowsFromEgt(...)` the method `readFlowsFromEGT()` return an `Array[Flow]`

We build a `MoveMatrix` (in reality a `Vector[TimeSlice, CellsMatrix]`) where `CellsMatrix` is an `Array[Array[Cell]]`  :

- A `Move` is an object with an index and a ratio.
- A `Cell` is a Map which associate an `AggregatedCategory` with an array of `Move`
- An `AggregatedCategory` object take a category (ex.`Education.SUP`) as input and return the coresponding aggregated category (ex. `HIGH`)
- A TimeSlice is a vector of TimeSlices (0-8,8-16,16-24) :
    - (0-8) - Empty Map for each Cell in Matrix
    - (8-16) - Empty Map for each Cell in Matrix
    - (16-24) - Empty Map for each Cell in Matrix

We first build a vector of `MoveMatrix` with empty array of Move for AggregatedCategory.

For each `TimeSlice` by reading flows from EGT file (`Array[Flow]`) and aggregate them by Cell (`addFlowToCell` function).

```
  for each nm in `Vector[TimeSlice, CellsMatrix]`  
   for each flow f in EGT  
     a) we get the Cell c at (x,y) of residence of current flow 
     b) we update these Cell c by calling addFlowToCell() function
``` 

The `addFlowToCell()` function add the contribution of a flow to `Cell` for a given timeslice by :
- computing the length of intersection between first `mn` timeslice and `f` timeslice.
- attributing an aggregated category `cat` to this flow.
- calculating :
    - if intersection is 0, cell `c` is not updated
    - else we create or update the `Array[Move]` for this `cat` .
    - If a `Move` already exist for the activity location of this flow, we update this Move by adding our contribution.

```scala
  def addFlowToCell(c: Cell, flow: Flow, timeSlice: TimeSlice): Cell = {
    val intersection = overlap(flow.timeSlice, timeSlice).toDouble
    val cat = AggregatedSocialCategory(SocialCategory(age = flow.age, sex = flow.sex, education = flow.education))

    if(intersection <= 0.0) c
    else
      c.get(cat) match {
        case Some(moves) =>
          val index = moves.indexWhere { m => Move.location.get(m) == flow.activity }
          if (index == -1) c + (cat -> moves.:+ (Move(flow.activity, intersection.toFloat)))
          else {
            val v = MoveMatrix.Move.ratio.get(moves(index))
            c + (cat -> moves.updated(index, Move(flow.activity, v + intersection.toFloat)))
          }
        case None =>
          c + (cat -> Array(Move(flow.activity, intersection.toFloat)))
      }
  }
```

After that, MoveMatrix is interpolated (`interpolate(...)`) then normalized (`normalizedFlows(...)`).

Because EGT flows are a small parts of real flow, we scale MoveMatrix CellsMatrix build using EGT at IDF level by interpolating values to neighbors. Each CellsMatrix could be interpreted as a probability by category at individual level.

To normalize CellsMatrix, we use `getMovesFromOppositeSex(...)`: in order to get more samples in areas where we have little information, we also use the information from samples in the area with the same categories but different sex



## Step 2 Simulating


- Chargement de `results/population.bin`   (ageCategory, sexe, education ) dans un vecteur d' `IndividualFeature`
- fct `generateWorld` :
    - `IndividualFeature` sont rewrappés dans des case class Age, Sexe, Education, Comportement, Lieu dans `Individual`
    - On filtre cette population en fonction du prédicat de la fonction `included`
    - Genere le monde `World` en fonction du vecteur `Individual`, fonction comportement de type `Behaviour` (double) et une graine  aléatoire. 
 



