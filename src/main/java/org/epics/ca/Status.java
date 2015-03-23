package org.epics.ca;

public enum Status
{
	NORMAL(0, Severity.SUCCESS, "Normal successful completion"),
	MAXIOC(1, Severity.ERROR, "Maximum simultaneous IOC connections exceeded"),
	UKNHOST(2, Severity.ERROR, "Unknown internet host"),
	UKNSERV(3, Severity.ERROR, "Unknown internet service"),
	SOCK(4, Severity.ERROR, "Unable to allocate a new socket"),
	CONN(5, Severity.WARNING, "Unable to connect to internet host or service"),
	ALLOCMEM(6, Severity.WARNING, "Unable to allocate additional dynamic memory"),
	UKNCHAN(7, Severity.WARNING, "Unknown IO channel"),
	UKNFIELD(8, Severity.WARNING, "Record field specified inappropriate for channel specified"),
	TOLARGE(9, Severity.WARNING, "The requested transfer is greater than available memory or EPICS_CA_MAX_ARRAY_BYTES"),
	TIMEOUT(10, Severity.WARNING, "User specified timeout on IO operation expired"),
	NOSUPPORT(11, Severity.WARNING, "Sorry, that feature is planned but not supported at this time"),
	STRTOBIG(12, Severity.WARNING, "The supplied string is unusually large"),
	DISCONNCHID(13, Severity.ERROR, "The request was ignored because the specified channel is disconnected"),
	BADTYPE(14, Severity.ERROR, "The data type specifed is invalid"),
	CHIDNOTFND(15, Severity.INFO, "Remote Channel not found"),
	CHIDRETRY(16, Severity.INFO, "Unable to locate all user specified channels"),
	INTERNAL(17, Severity.FATAL, "Channel Access Internal Failure"),
	DBLCLFAIL(18, Severity.WARNING, "The requested local DB operation failed"),
	GETFAIL(19, Severity.WARNING, "Could not perform a database value get for that channel"),
	PUTFAIL(20, Severity.WARNING, "Could not perform a database value put for that channel"),
	ADDFAIL(21, Severity.WARNING, "Could not perform a database monitor add for that channel"),
	BADCOUNT(22, Severity.WARNING, "Count requested inappropriate for that channel"),
	BADSTR(23, Severity.ERROR, "The supplied string has improper format"),
	DISCONN(24, Severity.WARNING, "Virtual circuit disconnect"),
	DBLCHNL(25, Severity.WARNING, "Identical process variable name on multiple servers"),
	EVDISALLOW(26, Severity.ERROR, "The CA routine called is inappropriate for use within an event handler"),
	BUILDGET(27, Severity.WARNING, "Database value get for that channel failed during channel search"),
	NEEDSFP(28, Severity.WARNING, "Unable to initialize without the vxWorks VX_FP_TASK task option set"),
	OVEVFAIL(29, Severity.WARNING, "Event queue overflow has prevented first pass event after event add"),
	BADMONID(30, Severity.ERROR, "bad monitor subscription identifier"),
	NEWADDR(31, Severity.WARNING, "Remote channel has new network address"),
	NEWCONN(32, Severity.INFO, "New or resumed network connection"),
	NOCACTX(33, Severity.WARNING, "Specified task isnt a member of a CA context"),
	DEFUNCT(34, Severity.FATAL, "Attempt to use defunct CA feature failed"),
	EMPTYSTR(35, Severity.WARNING, "The supplied string is empty"),
	NOREPEATER(36, Severity.WARNING, "Unable to spawn the CA repeater thread- auto reconnect will fail"),
	NOCHANMSG(37, Severity.WARNING, "No channel id match for search reply- search reply ignored"),
	DLCKREST(38, Severity.WARNING, "Reseting dead connection- will try to reconnect"),
	SERVBEHIND(39, Severity.WARNING, "Server (IOC) has fallen behind or is not responding- still waiting"),
	NOCAST(40, Severity.WARNING, "No internet interface with broadcast available"),
	BADMASK(41, Severity.ERROR, "The monitor selection mask supplied is empty or inappropriate"),
	IODONE(42, Severity.INFO, "IO operations have completed"),
	IOINPROGESS(43, Severity.INFO, "IO operations are in progress"),
	BADSYNCGRP(44, Severity.ERROR, "Invalid synchronous group identifier"),
	PUTCBINPROG(45, Severity.ERROR, "Put callback timed out"),
	NORDACCESS(46, Severity.WARNING, "Read access denied"),
	NOWTACCESS(47, Severity.WARNING, "Write access denied"),
	ANACHRONISM(48, Severity.ERROR, "Sorry, that anachronistic feature of CA is no longer supported"),
	NOSEARCHADDR(49, Severity.WARNING, "The search/beacon request address list was empty after initialization"),
	NOCONVERT(50, Severity.WARNING, "Data conversion between client's type and the server's type failed"),
	BADCHID(51, Severity.ERROR, "Invalid channel identifier"),
	BADFUNCPTR(52, Severity.ERROR, "Invalid function pointer"),
	ISATTACHED(53, Severity.WARNING, "Thread is already attached to a client context"),
	UNAVAILINSERV(54, Severity.WARNING, "No support in service"),
	CHANDESTROY(55, Severity.WARNING, "User destroyed channel"),
	BADPRIORITY(56, Severity.ERROR, "Priority out of range"),
	NOTTHREADED(57, Severity.ERROR, "Preemptive callback not enabled - additional threads may not join"),
	ARRAY16KCLIENT(58, Severity.WARNING, "Client's protocol revision does not support transfers exceeding 16k bytes"),
	CONNSEQTMO(59, Severity.WARNING, "Virtual circuit connection sequence aborted"),
	UNRESPTMO(60, Severity.WARNING, "Virtual circuit connection unresponsive");

	private final int value;
	private final Severity severity;
	private final String message;

	private Status(int value, Severity severity, String message) {
		this.value = value;
		this.severity = severity;
		this.message = message;
	}

	public int getValue() {
		return value;
	}

	public Severity getSeverity() {
		return severity;
	}

	public String getMessage() {
		return message;
	}

	public boolean isSuccessful() {
		return severity == Severity.SUCCESS;
	}
}
