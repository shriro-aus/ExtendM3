/*
 ***************************************************************
 *                                                             *
 *                           NOTICE                            *
 *                                                             *
 *   THIS SOFTWARE IS THE PROPERTY OF AND CONTAINS             *
 *   CONFIDENTIAL INFORMATION OF INFOR AND/OR ITS AFFILIATES   *
 *   OR SUBSIDIARIES AND SHALL NOT BE DISCLOSED WITHOUT PRIOR  *
 *   WRITTEN PERMISSION. LICENSED CUSTOMERS MAY COPY AND       *
 *   ADAPT THIS SOFTWARE FOR THEIR OWN USE IN ACCORDANCE WITH  *
 *   THE TERMS OF THEIR SOFTWARE LICENSE AGREEMENT.            *
 *   ALL OTHER RIGHTS RESERVED.                                *
 *                                                             *
 *   (c) COPYRIGHT 2020 INFOR.  ALL RIGHTS RESERVED.           *
 *   THE WORD AND DESIGN MARKS SET FORTH HEREIN ARE            *
 *   TRADEMARKS AND/OR REGISTERED TRADEMARKS OF INFOR          *
 *   AND/OR ITS AFFILIATES AND SUBSIDIARIES. ALL RIGHTS        *
 *   RESERVED.  ALL OTHER TRADEMARKS LISTED HEREIN ARE         *
 *   THE PROPERTY OF THEIR RESPECTIVE OWNERS.                  *
 *                                                             *
 ***************************************************************
 */

 import java.time.LocalDate;
 import java.time.LocalDateTime;
 import java.time.format.DateTimeFormatter;

 /*
 *Modification area - M3
 *Nbr               Date       User id        Description
 *EXT005            20240913   RDRIESSEN    Serial Number Creation for Suppliers monthly run
 *
 */

 /*
  * Add records to EXTVEN
  */

public class EXT330 extends ExtendM3Batch {
  private final LoggerAPI logger;
  private final DatabaseAPI database;
  private final BatchAPI batch;
  private final MICallerAPI miCaller;
  private final ProgramAPI program;
  private final IonAPI ion;

  private int xxCono;
  private int currentDate;
  private int currentTime;
  private boolean callAhs150Mi;
  private List lstMplind;
  private String ibitno;

  public EXT330(LoggerAPI logger, DatabaseAPI database, BatchAPI batch, MICallerAPI miCaller, ProgramAPI program, IonAPI ion) {
    this.logger = logger;
    this.database = database;
    this.batch = batch;
  	this.miCaller = miCaller;
  	this.program = program;
  	this.ion = ion;
  }

  public void main() {
    xxCono= program.LDAZD.CONO;

    if (!batch.getReferenceId().isPresent()) {
      return;
    }

    // Get parameters from EXTJOB
    Optional<String> data = getJobData(batch.getReferenceId().get());

    if (!data.isPresent()) {
      return;
    }

    currentDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")).toInteger();
    currentTime = Integer.valueOf(LocalDateTime.now().format(DateTimeFormatter.ofPattern("HHmmss")));
    String strCurrentDate = currentDate.toString();
    String yyyyMM = strCurrentDate.substring(0, 6);
    String strCurrentMonthStart = yyyyMM + "01";
    String MM = strCurrentDate.substring(4, 6);
    String YY = strCurrentDate.substring(2, 4);
    String DD = strCurrentDate.substring(6, 8);

    if (DD=="01") {
      Map<String,String>  params2 = [ "FILE": "SER001", "PK01": "SER001", "N096": "1"];
      Closure<?> callback = {
        Map<String, String> response1 ->
      }
      miCaller.call("CUSEXTMI","ChgFieldValue", params2, callback);
    }

    deleteFromEXTIND();

    int N096 = 1;

    Map<String, String> params = ["FILE":"SER001", "PK01":"SER001"];

    Closure<?> CUSEXTMICallback = {
      Map<String, String> response ->

      if(response.N096 != null) {
        try {
          float tempNo96 = Float.parseFloat(response.N096);
          N096 = (int)tempNo96;
          } catch (NumberFormatException e) {
        }
      }
    }

    miCaller.call("CUSEXTMI", "GetFieldValue", params, CUSEXTMICallback);

    ExpressionFactory expression = database.getExpressionFactory("MPLIND");
    expression = expression.eq("ICPUOS", "35").and(expression.eq("ICTRDT", strCurrentDate));
    DBAction queryMPLIND = database.table("MPLIND").index("00").matching(expression).selection("ICCONO", "ICSUNO","ICPUNO","ICPNLI", "ICTRDT", "ICRPQA", "ICPNLS", ).build();
    DBContainer container = queryMPLIND.getContainer();
    container.set("ICCONO", xxCono);
    lstMplind = new ArrayList();
    if(queryMPLIND.readAll(container, 1,10000, listMPLIND)){} else { return;}
    callAhs150Mi = false;

    if (lstMplind.size() > 1) {
      for (int j=0;j<lstMplind.size();j++) {
        String IBITNO = "";
        Map<String, String> recordMplind = (Map<String, String>) lstMplind[j];
        DBAction query = database.table("MPLINE").index("00").selection("IBCONO", "IBPUNO", "IBPNLI", "IBPNLS", "IBITNO").build();
        DBContainer mplineRecord = query.getContainer();
        mplineRecord.set("IBCONO", xxCono);
        mplineRecord.set("IBPUNO", recordMplind.ICPUNO);
        mplineRecord.set("IBPNLI", Integer.parseInt(recordMplind.ICPNLI));
        mplineRecord.set("IBPNLS", Integer.parseInt(recordMplind.ICPNLS));
        if (query.read(mplineRecord)) {
          IBITNO = mplineRecord.get("IBITNO").toString();
          String IBPUNO = "";
          IBPUNO = mplineRecord.get("IBPUNO").toString();
          DBAction query2 = database.table("MITMAS").index("00").selection("MMCONO", "MMCFI5").build();
          DBContainer mitmasRecord = query2.getContainer();
          mitmasRecord.set("MMCONO", xxCono);
          mitmasRecord.set("MMITNO", IBITNO);
          if (query2.read(mitmasRecord)) {
            String MMCFI5 = mitmasRecord.get("MMCFI5").toString();
            if (MMCFI5 == "Y") {
              callAhs150Mi = true;
              writeEXTIND(recordMplind.ICSUNO, recordMplind.ICPUNO, recordMplind.ICPNLI, IBITNO, recordMplind.ICTRDT, recordMplind.ICRPQA, recordMplind.ICCONO);
              float tempRpqa = Float.parseFloat(recordMplind.ICRPQA);
              int rpqaInt = (int)tempRpqa;
              for (int k=0;k<rpqaInt;k++) {
                String no96With6Digits = String.format("%06d", N096);
                String popn = YY+MM+N096;
                String remk = recordMplind.ICPUNO + "/" + recordMplind.ICPNLI;
                Map<String, String> paramsAddAlias = ["ALWT":"1", "ITNO": IBITNO, "POPN": popn, "REMK" : remk];
                Closure<?> MMS025MICallback = {
                  Map<String, String> response ->
                  if(response.errorMessage) {
                  }
                }
                miCaller.call("MMS025MI", "AddAlias", paramsAddAlias, MMS025MICallback);
                N096++;
                }
                String strN096 = N096.toString();
                Map<String,String> paramsChgFieldValue = [ "FILE": "SER001", "PK01": "SER001", "N096": strN096];
                Closure<?> callback = {
                  Map<String, String> response1 ->
                }
                miCaller.call("CUSEXTMI","ChgFieldValue", paramsChgFieldValue, callback);
              }
            }
          }
        }
      }

      if (callAhs150Mi) {
        Map<String,String>  params2 = [ "REPO": "EXT330", "REPV": "EXT330_REP", "SUBJ": strCurrentDate];
        Closure<?> callback = {
          Map<String, String> response1 ->
        }
        miCaller.call("AHS150MI","Submit", params2, callback);
      }
    }
  /*
	 * getJobData
	 *
	*/
  private Optional<String> getJobData(String referenceId) {
    DBAction queryEXTJOB = database.table("EXTJOB").index("00").selection("EXRFID", "EXJOID", "EXDATA").build();
    DBContainer EXTJOB = queryEXTJOB.createContainer();
    EXTJOB.set("EXCONO", xxCono);
    EXTJOB.set("EXRFID", referenceId);
    if (queryEXTJOB.read(EXTJOB)) {
      return Optional.of(EXTJOB.getString("EXDATA"));
    }
    return Optional.empty();
  }
  /*
   * deleteFromEXTIND - delete EXTIND table
   *
   */
   private deleteFromEXTIND() {
    DBAction queryEXTIND = database.table("EXTIND").index("00").build();
    DBContainer EXTIND = queryEXTIND.getContainer();
		EXTIND.set("EXCONO", xxCono);
		queryEXTIND.readAll(EXTIND, 1, 10000, deleteEXTIND);
   }
    /*
  * deleteEXTIND - Callback function
  *
  */
   Closure<?> deleteEXTIND = { LockedResult EXTIND ->
    EXTIND.delete();
   }

   /*
    * listMPLIND - Callback function to return EXTAPP
    *
    */
  Closure<?> listMPLIND = { DBContainer mplindRecords ->
    String suno = mplindRecords.get("ICSUNO").toString().trim();
    String puno = mplindRecords.get("ICPUNO").toString().trim();
    String pnli = mplindRecords.get("ICPNLI").toString().trim();
    String trdt = mplindRecords.get("ICTRDT").toString().trim();
    String rpqa = mplindRecords.get("ICRPQA").toString().trim();
    String pnls = mplindRecords.get("ICPNLS").toString().trim();
    String cono = mplindRecords.get("ICCONO").toString().trim();
    Map<String,String> params= ["ICSUNO":"${suno}".toString(), "ICPUNO":"${puno}".toString(), "ICPNLI":"${pnli}".toString(), "ICTRDT":"${trdt}".toString(), "ICRPQA":"${rpqa}".toString(), "ICPNLS":"${pnls}".toString(), "ICCONO":"${cono}".toString()] // toString is needed to convert from gstring to string
    lstMplind.add(params);
  }

  Closure<?> getAttribValues = { DBContainer container ->
    ibitno = container.get("IBITNO")
  }

  /*
	 * writeEXTIND - Write record to extensiton table EXTIND
	 *
	 */
	private writeEXTIND(String suno, String puno, String pnli, String itno, String trdt, String rpqa, String cono) {

	  DBAction actionEXTIND = database.table("EXTIND").build();
  	DBContainer EXTIND = actionEXTIND.getContainer();
  	EXTIND.set("EXCONO", Integer.parseInt(cono));
  	EXTIND.set("EXSUNO", suno);
  	EXTIND.set("EXPUNO", puno);
  	EXTIND.set("EXPNLI", Integer.parseInt(pnli));
  	EXTIND.set("EXITNO", itno);
  	EXTIND.set("EXTRDT", Integer.parseInt(trdt));
  	EXTIND.set("EXRPQA", Float.parseFloat(rpqa));
  	EXTIND.set("EXRGDT", currentDate);
  	EXTIND.set("EXRGTM", currentTime);
  	EXTIND.set("EXLMDT", currentDate);
  	EXTIND.set("EXCHNO", 0);
  	EXTIND.set("EXCHID", program.getUser());
    actionEXTIND.insert(EXTIND, recordExists);
  }
	/*
   * recordExists - return record already exists error message to the MI
   *
   */
  Closure recordExists = {
 }
}
