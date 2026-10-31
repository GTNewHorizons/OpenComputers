package li.cil.oc.integration.appeng

object DocHelper {
  private final val ItemFields = "requires 'name' OR 'id'. (Optional: `damage`, `size`, `tag`)"
  private final val FluidFields = "requires 'name' OR 'id'. (Optional: `size` or `amount`)"
  private final val EssentiaFields = "requires 'name'. (Optional: `size` or `amount`)"

  final val DeprecatedType = "[DEPRECATED] Passing 'type' as the 2nd argument is deprecated. Please include 'type' inside the 'detail' table instead.\n"
  private final val ItemTypeDetail = "- type=\"item\": " + ItemFields + "\n"
  private final val FluidTypeDetail = "- type=\"fluid\": " + FluidFields + "\n"
  private final val EssentiaTypeDetail = "- type=\"essentia\": " + EssentiaFields + " [Requires: Thaumic Energistics]\n"
  final val AllTypesDetail = "'detail' must contain 'type'. Supported types:\n" + ItemTypeDetail + FluidTypeDetail + EssentiaTypeDetail
  final val ItemTableOnlyDetail = "The `detail` table " + ItemFields
  final val FluidTableOnlyDetail = "The `detail` table " + FluidFields
  final val EssentiaTableOnlyDetail = "The `detail` table " + EssentiaFields
}
