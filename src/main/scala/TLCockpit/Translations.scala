package TLCockpit

import ru.makkarpov.scalingua.Messages

object Translations {
  val messages = Messages.compiled()
  // This assumes that you did not change the default settings of SBT key `localePackage`
  // Otherwise you should pass an actual value of it as argument of `compiled`
}
