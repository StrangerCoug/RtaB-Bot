package tel.discord.rtab.enums;

//Created by JerryEris <3

public enum PrizeType {
	//TROPHIES, woo winners
	S1TROPHY (   70000,"a replica of Vash's Season 1 trophy"),
	S2TROPHY (   60000,"a replica of Charles510's Season 2 trophy"),
	S3TROPHY (   54000,"a replica of Archstered's Season 3 trophy"),
	S4TROPHY (   52000,"a replica of Lavina's Season 4 trophy"),
	
	//(Ir)Regular prizes
	DB1	  (   22805,"a DesertBuck"),
	GOVERNOR  (   26000,"the Governor's favourite"),
	PI  	  (   31416,"a fresh pi"),
	ECONSTANT (   27183,"some e"),
	VOWEL     (   250,"a vowel"),
	QUESTION  (   64000,"the famous question"),
	BIGJON    (    1906,"the BigJon special"),	
	WEIRDAL   (   27000,"Weird Al's accordion"),
	ROCKEFELLER (  1273,"a trip down Rockefeller Street"),
	EWW       (     144,"something gross"),
	HUNDREDG  (  100000,"a 100 Grand bar"),
	HUNTER    (   22475,"Superportal codes"), //Aaron and Atia
	FEUD      (   20000,"Fast Money"), //MattR
	JOKERSIKE (       1,"a fake Joker"), //Lavina
	STCHARLES (     140,"a trip to St. Charles Place"), //MattR
	SNOOKER   (     147,"a snooker table"), //KP
	DARTBOARD (     180,"a dartboard"), //KP
	GRANDPIANO (  52000,"a grand piano"), //Jumble
	GOTOGO    (     200,"Advance to GO"),
	NORMALCD  (   54321,"a Normal Countdown"),
	ZONK      (     123,"a Zonk"),
	FREELIVES (    9900,"99 free lives! ...in Mario"),
	BUTTSPIE  (   18000,"a slice of Butterscotch Pie"),
	ITSART    (   35000,"art! Would you like to buy it... for ALL OF YOUR MONEY? No, it's... it's free. Art"),
	DISCORD   (   70013,"a Discord lamp"),
	PYRAMID   (  100000,"Pyramid"),
	BIFORCE   (   22222,"The Biforce"),
	SMALLTHINGS (   182,"All the Small Things");
    
    private final int prizeValue;
    private final String prizeName;

    PrizeType(int theValue, String theName) {
        this.prizeValue = theValue;
        this.prizeName = theName;
    }

    public String getPrizeName() {
        return prizeName;
    }

    public int getPrizeValue() {
        return prizeValue;
    }
}
