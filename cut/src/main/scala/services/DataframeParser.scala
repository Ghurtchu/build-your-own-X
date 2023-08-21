package services

import domain._

import scala.util.Try

trait DataframeParser extends Parser[Dataframe]

object DataframeParser {

  def ofComma: DataframeParser = of(Delimiter.Comma)

  def ofTab: DataframeParser = of(Delimiter.Tab)

  def of(delimiter: Delimiter): DataframeParser =
    (input: String) => {
      val lines = input.split("\n")
      Option
        .when(lines.length > 1) {
          val headers = lines.head
            .split(delimiter.repr)
            .map(_.replaceAll("[\uFEFF-\uFFFF]", ""))
          val rows = lines.tail.zipWithIndex.map {
            case (rowAsString, rowIndex) =>
              createRow(rowAsString, rowIndex, headers.indices, delimiter)
          }
          val columns = headers.zipWithIndex.map { case (header, index) =>
            createColumn(header, index, rows.toList)
          }.toList
          val isRowsDimensionsCorrect =
            rows.forall(_.values.length == headers.length)
          val columnCount = lines.length - 1
          val isColumnsDimensionsCorrect =
            columns.forall(_.values.length == columnCount)

          Option.when(isRowsDimensionsCorrect && isColumnsDimensionsCorrect) {
            Dataframe(columns)
          }
        }
        .flatten
    }

  private def createRow(
    rowAsString: String,
    rowIndex: Int,
    indexRange: Range,
    delimiter: Delimiter,
  ): Row =
    Row {
      rowAsString
        .split(delimiter.repr)
        .zip(indexRange)
        .map { case (rowValue, columnIndex) =>
          createCell(rowValue, columnIndex, rowIndex)
        }
        .toList
    }

  private def createCell(
    rowValue: String,
    columnIndex: Int,
    rowIndex: Int,
  ): Cell =
    Cell(
      Position.from(rowIndex, columnIndex).getOrElse(Position.zero),
      rowValue,
    )

  private def createColumn(
    header: String,
    index: Int,
    rows: List[Row],
  ): Column =
    Column(Header(header), rows.map(_.values(index)))

}
