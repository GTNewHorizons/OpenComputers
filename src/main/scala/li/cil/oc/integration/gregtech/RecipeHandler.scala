package li.cil.oc.integration.gregtech

import java.util
import com.typesafe.config.Config
import com.typesafe.config.ConfigValue
import gregtech.api.enums.GT_Values
import gregtech.api.recipe.RecipeMaps
import li.cil.oc.common.recipe.Recipes
import li.cil.oc.common.recipe.Recipes.RecipeException
import net.minecraft.item.ItemStack
import net.minecraftforge.oredict.OreDictionary

import scala.collection.convert.WrapAsScala._

object RecipeHandler {
  def init(): Unit = {
    Recipes.registerRecipeHandler("gt_alloySmelter", addGTAlloySmelterRecipe)
    Recipes.registerRecipeHandler("gt_assembler", addGTAssemblingMachineRecipe)
    Recipes.registerRecipeHandler("gt_bender", addGTBenderRecipe)
    Recipes.registerRecipeHandler("gt_canner", addGTCannerRecipe)
    Recipes.registerRecipeHandler("gt_chemical", addGTChemicalRecipe)
    Recipes.registerRecipeHandler("gt_cutter", addGTCutterRecipe)
    Recipes.registerRecipeHandler("gt_fluidCanner", addGTFluidCannerRecipe)
    Recipes.registerRecipeHandler("gt_formingPress", addGTFormingPressRecipe)
    Recipes.registerRecipeHandler("gt_lathe", addGTLatheRecipe)
    Recipes.registerRecipeHandler("gt_laserEngraver", addGTLaserEngraverRecipe)
    Recipes.registerRecipeHandler("gt_wiremill", addGTWireMillRecipe)
  }

  def addGTAlloySmelterRecipe(output: ItemStack, recipe: Config) {
    val (primaryInputs, secondaryInputs, _, _, _, eu, duration) = parseRecipe(output, recipe)
    secondaryInputs match {
      case Some(value) =>
        for (primaryInput <- primaryInputs; secondaryInput <- value) {
          GT_Values.RA.stdBuilder().itemInputs(primaryInput, secondaryInput).itemOutputs(output).duration(duration)
            .eut(eu).addTo(RecipeMaps.alloySmelterRecipes)
        }
      case _ =>
        for (primaryInput <- primaryInputs) {
          GT_Values.RA.stdBuilder().itemInputs(primaryInput).itemOutputs(output).duration(duration)
            .eut(eu).addTo(RecipeMaps.alloySmelterRecipes)
        }
    }
  }

  def addGTAssemblingMachineRecipe(output: ItemStack, recipe: Config) {
    val (primaryInputs, secondaryInputs, fluidInput, _, _, eu, duration) = parseRecipe(output, recipe)
    secondaryInputs match {
      case Some(value) =>
        for (primaryInput <- primaryInputs; secondaryInput <- value) {
          var recipe = GT_Values.RA.stdBuilder().itemInputs(primaryInput, secondaryInput)
          fluidInput match {
            case Some(fluidStack) =>
              recipe = recipe.fluidInputs(fluidStack)
            case _ =>
          }
          recipe.itemOutputs(output).duration(duration).eut(eu).addTo(RecipeMaps.assemblerRecipes)
        }
      case _ =>
        for (primaryInput <- primaryInputs) {
          var recipe = GT_Values.RA.stdBuilder().itemInputs(primaryInput)
          fluidInput match {
            case Some(fluidStack) =>
              recipe = recipe.fluidInputs(fluidStack)
            case _ =>
          }
          recipe.itemOutputs(output).duration(duration).eut(eu).addTo(RecipeMaps.assemblerRecipes)
        }
    }
  }

  def addGTBenderRecipe(output: ItemStack, recipe: Config) {
    val (primaryInputs, _, _, _, _, eu, duration) = parseRecipe(output, recipe)
    for (primaryInput <- primaryInputs) {
      GT_Values.RA.stdBuilder().itemInputs(primaryInput).itemOutputs(output).duration(duration).eut(eu)
        .addTo(RecipeMaps.benderRecipes)
    }
  }

  def addGTCannerRecipe(output: ItemStack, recipe: Config) {
    val (primaryInputs, secondaryInputs, _, _, secondaryOutputs, eu, duration) = parseRecipe(output, recipe)
    val secondaryOutput = secondaryOutputs.headOption
    secondaryInputs match {
      case Some(value) =>
        for (primaryInput <- primaryInputs; secondaryInput <- value) {
          var recipe = GT_Values.RA.stdBuilder().itemInputs(primaryInput, secondaryInput)
          secondaryOutput match {
            case Some(itemStack) =>
              recipe = recipe.itemOutputs(output, itemStack)
            case _ =>
              recipe = recipe.itemOutputs(output)
          }
          recipe.duration(duration).eut(eu).addTo(RecipeMaps.cannerRecipes)
        }
      case None =>
        for (primaryInput <- primaryInputs) {
          var recipe = GT_Values.RA.stdBuilder().itemInputs(primaryInput)
          secondaryOutput match {
            case Some(itemStack) =>
              recipe = recipe.itemOutputs(output, itemStack)
            case _ =>
              recipe = recipe.itemOutputs(output)
          }
          recipe.duration(duration).eut(eu).addTo(RecipeMaps.cannerRecipes)
        }
    }
  }

  def addGTChemicalRecipe(output: ItemStack, recipe: Config) {
    val (primaryInputs, secondaryInputs, fluidInput, fluidOutput, _, eu, duration) = parseRecipe(output, recipe)
    secondaryInputs match {
      case Some(value) =>
        for (primaryInput <- primaryInputs; secondaryInput <- value) {
          var recipe = GT_Values.RA.stdBuilder().itemInputs(primaryInput, secondaryInput)
          fluidInput match {
            case Some(fluidStack) =>
              recipe = recipe.fluidInputs(fluidStack)
            case _ =>
          }
          fluidOutput match {
            case Some(fluidStack) =>
              recipe = recipe.fluidOutputs(fluidStack)
            case _ =>
          }
          recipe.itemOutputs(output).duration(duration).eut(eu).addTo(RecipeMaps.chemicalReactorRecipes)
        }
      case _ =>
        for (primaryInput <- primaryInputs) {
          var recipe = GT_Values.RA.stdBuilder().itemInputs(primaryInput)
          fluidInput match {
            case Some(fluidStack) =>
              recipe = recipe.fluidInputs(fluidStack)
            case _ =>
          }
          fluidOutput match {
            case Some(fluidStack) =>
              recipe = recipe.fluidOutputs(fluidStack)
            case _ =>
          }
          recipe.itemOutputs(output).duration(duration).eut(eu).addTo(RecipeMaps.chemicalReactorRecipes)
        }
    }
  }

  def addGTCutterRecipe(output: ItemStack, recipe: Config) {
    val (primaryInputs, _, fluidInput, _, secondaryOutputs, eu, duration) = parseRecipe(output, recipe)
    val secondaryOutput = secondaryOutputs.headOption
    for (primaryInput <- primaryInputs) {
      var recipe = GT_Values.RA.stdBuilder().itemInputs(primaryInput)
      fluidInput match {
        case Some(fluidStack) =>
          recipe = recipe.fluidInputs(fluidStack)
        case _ =>
      }
      secondaryOutput match {
        case Some(itemStack) =>
          recipe = recipe.itemOutputs(output, itemStack)
        case _ =>
          recipe = recipe.itemOutputs(output)
      }
      recipe.duration(duration).eut(eu).addTo(RecipeMaps.cutterRecipes)
    }
  }

  def addGTFluidCannerRecipe(output: ItemStack, recipe: Config) {
    val (primaryInputs, _, fluidInput, fluidOutput, _, eu, duration) = parseRecipe(output, recipe)
    for (primaryInput <- primaryInputs) {
      var recipe = GT_Values.RA.stdBuilder().itemInputs(primaryInput)
      fluidInput match {
        case Some(fluidStack) =>
          recipe = recipe.fluidInputs(fluidStack)
        case _ =>
      }
      fluidOutput match {
        case Some(fluidStack) =>
          recipe = recipe.fluidOutputs(fluidStack)
        case _ =>
      }
      recipe.itemOutputs(output).duration(duration).eut(eu).addTo(RecipeMaps.fluidCannerRecipes)
    }
  }

  def addGTFormingPressRecipe(output: ItemStack, recipe: Config) {
    val (primaryInputs, secondaryInputs, _, _, _, eu, duration) = parseRecipe(output, recipe)
    secondaryInputs match {
      case Some(value) =>
        for (primaryInput <- primaryInputs; secondaryInput <- value) {
          GT_Values.RA.stdBuilder().itemInputs(primaryInput, secondaryInput).itemOutputs(output).duration(duration)
            .eut(eu).addTo(RecipeMaps.formingPressRecipes)
        }
      //all values required
      case _ =>
    }
  }

  def addGTLatheRecipe(output: ItemStack, recipe: Config) {
    val (primaryInputs, _, _, _, secondaryOutputs, eu, duration) = parseRecipe(output, recipe)
    val secondaryOutput = secondaryOutputs.headOption
    for (primaryInput <- primaryInputs) {
      var recipe = GT_Values.RA.stdBuilder().itemInputs(primaryInput)
      secondaryOutput match {
        case Some(itemStack) =>
          recipe = recipe.itemOutputs(output, itemStack)
        case _ =>
          recipe = recipe.itemOutputs(output)
      }
      recipe.duration(duration).eut(eu).addTo(RecipeMaps.latheRecipes)
    }
  }

  def addGTLaserEngraverRecipe(output: ItemStack, recipe: Config) {
    val (primaryInputs, secondaryInputs, _, _, _, eu, duration) = parseRecipe(output, recipe)
    secondaryInputs match {
      case Some(value) =>
        for (primaryInput <- primaryInputs; secondaryInput <- value) {
          GT_Values.RA.stdBuilder().itemInputs(primaryInput, secondaryInput).itemOutputs(output).duration(duration)
            .eut(eu).addTo(RecipeMaps.laserEngraverRecipes)
        }
      case _ =>
    }
  }

  def addGTWireMillRecipe(output: ItemStack, recipe: Config) {
    val (primaryInputs, _, _, _, _, eu, duration) = parseRecipe(output, recipe)
    for (primaryInput <- primaryInputs) {
      GT_Values.RA.stdBuilder().itemInputs(primaryInput).itemOutputs(output).duration(duration).eut(eu)
        .addTo(RecipeMaps.wiremillRecipes)
    }
  }

  private def parseRecipe(output: ItemStack, recipe: Config) = {
    val inputs = parseIngredientList(recipe.getValue("input")).toBuffer
    output.stackSize = Recipes.tryGetCount(recipe)

    if (inputs.size < 1 || inputs.size > 2) {
      throw new RecipeException(s"Invalid recipe length: ${inputs.size}, should be 1 or 2.")
    }

    val inputCount = recipe.getIntList("count")
    if (inputCount.size() != inputs.size) {
      throw new RecipeException(s"Mismatched ingredient count: ${inputs.size} != ${inputCount.size}.")
    }

    (inputs, inputCount).zipped.foreach((stacks, count) =>
      stacks.foreach(stack =>
        if (stack != null && count > 0)
          stack.stackSize = stack.getMaxStackSize min count))

    inputs.padTo(2, null)

    val outputs =
      if (recipe.hasPath("secondaryOutput")) {
        val secondaryOutput = parseIngredientList(recipe.getValue("secondaryOutput")).map(_.headOption)

        val outputCount = recipe.getIntList("secondaryOutputCount")
        if (outputCount.size() != secondaryOutput.size) {
          throw new RecipeException(s"Mismatched secondary output count: ${secondaryOutput.size} != ${outputCount.size}.")
        }

        (secondaryOutput, outputCount).zipped.foreach((stack, count) =>
          if (count > 0) stack.foreach(s => s.stackSize = s.getMaxStackSize min count))
        secondaryOutput.collect { case Some(stack) => stack }
      }
      else Iterable.empty[ItemStack]

    val inputFluidStack =
      if (recipe.hasPath("inputFluid")) Recipes.parseFluidIngredient(recipe.getConfig("inputFluid"))
      else None

    val outputFluidStack =
      if (recipe.hasPath("outputFluid")) Recipes.parseFluidIngredient(recipe.getConfig("outputFluid"))
      else None

    val eu = recipe.getInt("eu")
    val duration = recipe.getInt("time")

    (inputs.head, Option(inputs.last), inputFluidStack, outputFluidStack, outputs, eu, duration)
  }

  private def parseIngredientList(list: ConfigValue) =
    (list.unwrapped() match {
      case list: util.List[AnyRef]@unchecked => list.map(Recipes.parseIngredient)
      case other => Iterable(Recipes.parseIngredient(other))
    }) map {
      case null => Array.empty[ItemStack]
      case stack: ItemStack => Array(stack)
      case name: String => Array(OreDictionary.getOres(name): _*)
      case other => throw new RecipeException(s"Invalid ingredient type: $other.")
    }
}
